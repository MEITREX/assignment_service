package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.event.Response;
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
     */
    protected AssignmentCompletedFeedback publishProgress(final LogAssignmentCompletedInput input, final UUID userId) {
        final double requiredPercentage = 0.5;
        final AssignmentEntity assignmentEntity = requireAssignmentExists(input.getAssessmentId());

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

    public Exercise createExercise(final UUID assessmentId, final CreateExerciseInput createExerciseInput) {
        assignmentValidator.validateCreateExerciseInput(createExerciseInput);

        ExerciseEntity newExerciseEntity = assignmentMapper.createExerciseInputToEntity(createExerciseInput);
        AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);
        List<ExerciseEntity> assignmentExercises = assignmentEntity.getExercises();

        newExerciseEntity.setParentAssignment(assignmentEntity);
        assignmentExercises.add(newExerciseEntity);

        AssignmentEntity savedAssignmentEntity = assignmentRepository.save(assignmentEntity);
        return null; // TODO return Assignment or Exercise?
    }

}
