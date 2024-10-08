package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentChangeEvent;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.event.CrudOperation;
import de.unistuttgart.iste.meitrex.common.event.Response;
import de.unistuttgart.iste.meitrex.common.exception.IncompleteEventMessageException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.user_handling.UserCourseAccessValidator.validateUserHasAccessToCourse;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentValidator assignmentValidator;
    private final TopicPublisher topicPublisher;

    /**
     * Returns all assignments that are linked to the given assessment ids
     *
     * @param ids list of assessment ids
     * @return list of assignments, an element is null if the corresponding assessment id was not found
     */
    public List<Assignment> findAssignmentsByAssessmentIds(final List<UUID> ids) {
        return assignmentRepository.findAllByIdPreservingOrder(ids).stream()
                .map(assignmentMapper::assignmentEntityToDto)
                .toList();
    }

    /**
     * Creates a new assignment
     *
     * @param courseId id of the course the assignment is in
     *                 (must be the same as the course id of the assessment)
     * @param assessmentId id of the corresponding assessment
     * @param createAssignmentInput input data for creating the assignment
     * @return A new assignment
     * @throws ValidationException if the assignment input is invalid according
     *                              to {@link AssignmentValidator#validateCreateAssignmentInput(CreateAssignmentInput)}
     */
    public Assignment createAssignment(final UUID courseId, final UUID assessmentId, final CreateAssignmentInput createAssignmentInput) {
        assignmentValidator.validateCreateAssignmentInput(createAssignmentInput);

        final AssignmentEntity mappedAssignmentEntity = assignmentMapper.createAssignmentInputToEntity(createAssignmentInput);
        mappedAssignmentEntity.setAssessmentId(assessmentId);
        mappedAssignmentEntity.setCourseId(courseId);

        final AssignmentEntity savedAssignmentEntity = assignmentRepository.save(mappedAssignmentEntity);
        return assignmentMapper.assignmentEntityToDto(savedAssignmentEntity);
    }


    /**
     * Creates AssignmentMutation and validates admin access to course.
     *
     * @param assessmentId id of the assessment being modified
     * @param currentUser id of the current user
     * @return an AssignmentMutation containing the assignment's id
     * @throws EntityNotFoundException if the assignment does not exist
     */
    public AssignmentMutation mutateAssignment(final UUID assessmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);

        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, assignmentEntity.getCourseId());

        // this is basically an empty object, only serving as a parent for the nested mutations
        return new AssignmentMutation(assessmentId);
    }

    /**
     * Validates user access to the course and publishes a user's progress.
     *
     * @param input contains achieved credits for assignment, exercises and subexercises
     * @param currentUser the user that is currently logged in
     * @return Feedback containing success and correctness data
     * @throws EntityNotFoundException if the assignment does not exist
     */
    public AssignmentCompletedFeedback logAssignmentCompleted(final LogAssignmentCompletedInput input, final LoggedInUser currentUser) {
        final AssignmentEntity assignmentEntity = this.requireAssignmentExists(input.getAssessmentId());

        // TODO adjust required role during further development
        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.TUTOR, assignmentEntity.getCourseId());

        // TODO the current user should not be the user that will be associated with the AssignmentCompletedInput, as the current user will be a tutor not the student
        // unless a student presses a refresh button, which checks for data in TMS
        return this.publishProgress(input, currentUser.getId());
    }

    /**
     * Publishes the {@link ContentProgressedEvent} to the dapr pubsub.
     *
     * @param input contains achieved credits for assignment, exercises and subexercises
     * @param userId id of the user who did the assignment
     * @return Feedback containing success and correctness data
     * @throws EntityNotFoundException if the assignment does not exist
     */
    protected AssignmentCompletedFeedback publishProgress(final LogAssignmentCompletedInput input, final UUID userId) {
        assignmentValidator.validateLogAssignmentCompletedInput(input);

        final AssignmentEntity assignmentEntity = requireAssignmentExists(input.getAssessmentId());
        final double requiredPercentage = assignmentEntity.getRequiredPercentage() == null ? 0.5 : assignmentEntity.getRequiredPercentage();

        // TODO is "updateExerciseStatistics(input, userId, assignmentEntity)" needed?

        final double achievedCredits = input.getAchievedCredits();
        final double totalCredits = assignmentEntity.getTotalCredits();

        final boolean success = achievedCredits >= requiredPercentage * totalCredits;
        final double correctness = achievedCredits / totalCredits;

        // create Responses for each exercise and subexercise
        final List<Response> responses = new ArrayList<>();
        for (final ExerciseCompletedInput exerciseCompletedInput: input.getCompletedExercises()) {
            final UUID exerciseId = exerciseCompletedInput.getItemId();
            final ExerciseEntity exerciseEntity = findExerciseEntityInAssignmentEntity(exerciseId, assignmentEntity);
            final double totalExerciseCredits = exerciseEntity.getTotalExerciseCredits();
            final float achievedExercisePercentage = totalExerciseCredits == 0 ? 1 : (float) (exerciseCompletedInput.getAchievedCredits() / totalExerciseCredits);
            final Response exerciseResponse = new Response(exerciseId, achievedExercisePercentage);
            responses.add(exerciseResponse);

            for (final SubexerciseCompletedInput subexerciseCompletedInput: exerciseCompletedInput.getCompletedSubexercises()) {
                final UUID subexerciseId = subexerciseCompletedInput.getItemId();
                final SubexerciseEntity subexerciseEntity = findSubexerciseEntityInExerciseEntity(subexerciseId, exerciseEntity);
                final double totalSubexerciseCredits = subexerciseEntity.getTotalSubexerciseCredits();
                final float achievedSubexercisePercentage = totalSubexerciseCredits == 0 ? 1 : (float) (subexerciseCompletedInput.getAchievedCredits() / totalSubexerciseCredits);
                final Response subexerciseResponse = new Response(subexerciseId, achievedSubexercisePercentage);
                responses.add(subexerciseResponse);
            }
        }

        // create new user progress event message
        final ContentProgressedEvent userProgressLogEvent = ContentProgressedEvent.builder()
                .userId(userId)
                .contentId(assignmentEntity.getAssessmentId())
                .hintsUsed(0)
                .success(success)
                .timeToComplete(null)
                .correctness(correctness)
                .responses(responses)
                .build();

        // publish new user progress event message
        topicPublisher.notifyUserWorkedOnContent(userProgressLogEvent);
        return AssignmentCompletedFeedback.builder()
                .setCorrectness(correctness)
                .setSuccess(success)
                .build();
    }

    /**
     * Creates an exercise and adds it to the respective assignment.
     *
     * @param assessmentId the id of the assignment the exercise should be added to
     * @param createExerciseInput input data for creating the exercise
     * @return the new exercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws ValidationException if the exercise input is invalid according
     *                                    to {@link AssignmentValidator#validateCreateExerciseInput(CreateExerciseInput)}
     */
    public Exercise createExercise(final UUID assessmentId, final CreateExerciseInput createExerciseInput) {
        assignmentValidator.validateCreateExerciseInput(createExerciseInput);

        AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);
        List<ExerciseEntity> assignmentExercises = assignmentEntity.getExercises();

        ExerciseEntity newExerciseEntity = assignmentMapper.createExerciseInputToEntity(createExerciseInput);
        newExerciseEntity.setParentAssignment(assignmentEntity);
        assignmentExercises.add(newExerciseEntity);

        assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() + createExerciseInput.getTotalExerciseCredits());

        assignmentRepository.save(assignmentEntity);
        return assignmentMapper.exerciseEntityToDto(newExerciseEntity);
    }

    /**
     * Updates the exercise with the given id. Also updates the respective assignment.
     *
     * @param assessmentId the id of the assignment the exercise is in
     * @param updateExerciseInput input data for updating the exercise, also contains the id
     * @return the updated exercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws ValidationException if the exercise input is invalid according
     *                                         to {@link AssignmentValidator#validateUpdateExerciseInput(UpdateExerciseInput)}
     */
    public Exercise updateExercise(final UUID assessmentId, final UpdateExerciseInput updateExerciseInput) {
        assignmentValidator.validateUpdateExerciseInput(updateExerciseInput);

        AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);

        ExerciseEntity oldExerciseEntity = this.findExerciseEntityInAssignmentEntity(updateExerciseInput.getItemId(), assignmentEntity);
        ExerciseEntity newExerciseEntity = assignmentMapper.updateExerciseInputToEntity(updateExerciseInput);
        newExerciseEntity.setParentAssignment(assignmentEntity);

        final int exerciseIndex = assignmentEntity.getExercises().indexOf(oldExerciseEntity);
        assignmentEntity.getExercises().set(exerciseIndex, newExerciseEntity);

        assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() - oldExerciseEntity.getTotalExerciseCredits() + updateExerciseInput.getTotalExerciseCredits());

        assignmentRepository.save(assignmentEntity);
        return assignmentMapper.exerciseEntityToDto(newExerciseEntity);
    }

    /**
     * Deletes the exercise with the given id and removes it from the assignment.
     * Publishes an ItemChangeEvent.
     *
     * @param assessmentId the id of the assignment the exercise is in
     * @param exerciseId the id of the exercise
     * @return the id of the deleted exercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws EntityNotFoundException if the exercise can't be found in the assignment
     */
    public UUID deleteExercise(final UUID assessmentId, final UUID exerciseId) {
        final AssignmentEntity assignmentEntity = requireAssignmentExists(assessmentId);
        try {
            final ExerciseEntity oldExerciseEntity = this.findExerciseEntityInAssignmentEntity(exerciseId, assignmentEntity);
            assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() - oldExerciseEntity.getTotalExerciseCredits());
            assignmentEntity.getExercises().remove(oldExerciseEntity);
        } catch (Exception e) {
            throw new EntityNotFoundException("Exercise with itemId %s not found.".formatted(exerciseId));
        }
        assignmentRepository.save(assignmentEntity);
        publishItemChangeEvent(exerciseId);
        return exerciseId;
    }

    /**
     * Creates a subexercise and adds it to the given assignment.
     *
     * @param assessmentId the id of the assignment
     * @param createSubexerciseInput input data for creating the subexercise, also contains the id of the parent exercise
     * @return the new subexercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws ValidationException if the subexercise input is invalid according
     *                                               to {@link AssignmentValidator#validateCreateSubexerciseInput(CreateSubexerciseInput)}
     */
    public Subexercise createSubexercise(final UUID assessmentId, final CreateSubexerciseInput createSubexerciseInput) {
        assignmentValidator.validateCreateSubexerciseInput(createSubexerciseInput);

        AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);

        SubexerciseEntity subexerciseEntity = assignmentMapper.createSubexerciseInputToEntity(createSubexerciseInput);
        ExerciseEntity parentExerciseEntity = findExerciseEntityInAssignmentEntity(createSubexerciseInput.getParentExerciseId(), assignmentEntity);
        subexerciseEntity.setParentExercise(parentExerciseEntity);

        parentExerciseEntity.getSubexercises().add(subexerciseEntity);

        parentExerciseEntity.setTotalExerciseCredits(parentExerciseEntity.getTotalExerciseCredits() + createSubexerciseInput.getTotalSubexerciseCredits());
        assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() + createSubexerciseInput.getTotalSubexerciseCredits());

        assignmentRepository.save(assignmentEntity);
        return assignmentMapper.subexerciseEntityToDto(subexerciseEntity);
    }

    /**
     * Updates the subexercise with the given id. Also updates the respective assignment and exercise.
     *
     * @param assessmentId the id of the assignment
     * @param updateSubexerciseInput the data for updating the subexercise, also contains the subexerciseId
     * @return the updated subexercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws ValidationException ValidationException if the subexercise input is invalid according
     *                                               to {@link AssignmentValidator#validateUpdateSubexerciseInput(UpdateSubexerciseInput)}
     */
    public Subexercise updateSubexercise(final UUID assessmentId, final UpdateSubexerciseInput updateSubexerciseInput) {
        assignmentValidator.validateUpdateSubexerciseInput(updateSubexerciseInput);
        SubexerciseEntity newSubexerciseEntity = assignmentMapper.updateSubexerciseInputToEntity(updateSubexerciseInput);

        UUID subexerciseId = updateSubexerciseInput.getItemId();
        AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);
        SubexerciseEntity oldSubexerciseEntity = this.findSubexerciseEntityInAssignmentEntity(subexerciseId, assignmentEntity);
        ExerciseEntity parentExerciseEntity = oldSubexerciseEntity.getParentExercise();
        newSubexerciseEntity.setParentExercise(parentExerciseEntity);

        final int subexerciseIndex = parentExerciseEntity.getSubexercises().indexOf(oldSubexerciseEntity);
        parentExerciseEntity.getSubexercises().set(subexerciseIndex, newSubexerciseEntity);

        final double creditDifference = updateSubexerciseInput.getTotalSubexerciseCredits() - oldSubexerciseEntity.getTotalSubexerciseCredits();
        parentExerciseEntity.setTotalExerciseCredits(parentExerciseEntity.getTotalExerciseCredits() + creditDifference);
        assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() + creditDifference);

        assignmentRepository.save(assignmentEntity);
        return assignmentMapper.subexerciseEntityToDto(newSubexerciseEntity);
    }

    /**
     * Deletes the subexercise with the given id and removes it from the assignment.
     * Publishes an ItemChangeEvent.
     *
     * @param assessmentId the id of the assignment the subexercise is in
     * @param subexerciseId the id of the subexercise
     * @return the id of the deleted subexercise
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws EntityNotFoundException if the subexercise can't be found in the assignment
     */
    public UUID deleteSubexercise(final UUID assessmentId, final UUID subexerciseId) {
        final AssignmentEntity assignmentEntity = requireAssignmentExists(assessmentId);
        SubexerciseEntity subexerciseEntity = this.findSubexerciseEntityInAssignmentEntity(subexerciseId, assignmentEntity);
        ExerciseEntity parentExerciseEntity = subexerciseEntity.getParentExercise();

        final double subexerciseCredits = subexerciseEntity.getTotalSubexerciseCredits();
        parentExerciseEntity.setTotalExerciseCredits(parentExerciseEntity.getTotalExerciseCredits() - subexerciseCredits);
        assignmentEntity.setTotalCredits(assignmentEntity.getTotalCredits() - subexerciseCredits);

        parentExerciseEntity.getSubexercises().remove(subexerciseEntity);

        assignmentRepository.save(assignmentEntity);
        publishItemChangeEvent(subexerciseId);
        return subexerciseId;
    }

    /**
     * Returns the assignment with the given id or throws an exception if the assignment does not exist.
     *
     * @param assessmentId the id of the assignment
     * @return the assignment entity
     * @throws EntityNotFoundException if the assignment does not exist
     */
    public AssignmentEntity requireAssignmentExists(final UUID assessmentId) {
        return assignmentRepository.findById(assessmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment with assessmentId %s not found".formatted(assessmentId)));
    }

    /**
     * Find an exercise-entity in an assignment-entity's list of exercises by the given ID.
     *
     * @param exerciseId the id of the ExerciseEntity
     * @param assignmentEntity the AssignmentEntity in which the exercise should be found
     * @return the ExerciseEntity with the given id
     * @throws EntityNotFoundException if the exercise isn't found in the assignmentEntity
     */
    protected ExerciseEntity findExerciseEntityInAssignmentEntity(final UUID exerciseId, final AssignmentEntity assignmentEntity) {
        return assignmentEntity.getExercises().stream()
                .filter(exerciseEntity -> exerciseId.equals(exerciseEntity.getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Exercise with itemId %s not found in assignmentEntity".formatted(exerciseId)));
    }

    /**
     * Find a subexercise-entity in an exercise-entity's list of subexercises by the given ID.
     *
     * @param subexerciseId the id of the SubexerciseEntity
     * @param exerciseEntity the ExerciseEntity in which the subexercise should be found
     * @return the SubexerciseEntity with the given id
     * @throws EntityNotFoundException if the subexercise isn't found in the exerciseEntity
     */
    protected SubexerciseEntity findSubexerciseEntityInExerciseEntity(final UUID subexerciseId, final ExerciseEntity exerciseEntity) {
        return exerciseEntity.getSubexercises().stream()
                .filter(subexerciseEntity -> subexerciseId.equals(subexerciseEntity.getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Subexercise with itemId %s not found in exerciseEntity".formatted(subexerciseId)));
    }

    /**
     * Find a subexercise-entity that is part of an assignment-entity, meaning the subexercise is part of an exercise which is part of the assignment.
     *
     * @param subexerciseId the id of the SubexerciseEntity
     * @param assignmentEntity the AssignmentEntity in which the subexercise should be found
     * @return the SubexerciseEntity with the given id
     * @throws EntityNotFoundException if the subexercise isn't found in the assignmentEntity
     */
    protected SubexerciseEntity findSubexerciseEntityInAssignmentEntity(final UUID subexerciseId, final AssignmentEntity assignmentEntity) {
        for (final ExerciseEntity exerciseEntity : assignmentEntity.getExercises()) {
            for (final SubexerciseEntity subexerciseEntity : exerciseEntity.getSubexercises()) {
                if (subexerciseEntity.getId().equals(subexerciseId)) {
                    return subexerciseEntity;
                }
            }
        }
        throw new EntityNotFoundException("Subexercise with itemId %s not found in assignment".formatted(subexerciseId));
    }

    /**
     * removes all assignments when linked Content gets deleted
     *
     * @param dto event object containing changes to content
     */
    public void deleteAssignmentIfContentIsDeleted(final ContentChangeEvent dto) throws IncompleteEventMessageException {

        // validate event message
        checkCompletenessOfDto(dto);

        // only consider DELETE Operations
        if (!dto.getOperation().equals(CrudOperation.DELETE) || dto.getContentIds().isEmpty()) {
            return;
        }

        assignmentRepository.deleteAllById(dto.getContentIds());
    }

    /**
     * helper function to make sure received event message is complete
     *
     * @param dto event message under evaluation
     * @throws IncompleteEventMessageException if any of the fields are null
     */
    private void checkCompletenessOfDto(final ContentChangeEvent dto) throws IncompleteEventMessageException {
        if (dto.getOperation() == null || dto.getContentIds() == null) {
            throw new IncompleteEventMessageException(IncompleteEventMessageException.ERROR_INCOMPLETE_MESSAGE);
        }
    }

    /**
     * helper function, that creates a ItemChange Event and publishes it, when an exercise was deleted
     * @param itemId the id of the item
     */
    private void publishItemChangeEvent(final UUID itemId) {
        topicPublisher.notifyItemChanges(itemId, CrudOperation.DELETE);
    }
}
