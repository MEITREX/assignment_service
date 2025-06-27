package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExternalCourseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCourseRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentChangeEvent;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.event.CrudOperation;
import de.unistuttgart.iste.meitrex.common.event.Response;
import de.unistuttgart.iste.meitrex.common.exception.IncompleteEventMessageException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.CodeAssignmentMetadataEntity;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CourseServiceClient courseServiceClient;
    private final ContentServiceClient contentServiceClient;
    private final CodeAssessmentProvider codeAssessmentProvider;
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository;
    private final ExternalCourseRepository externalCourseRepository;
    private final GradingRepository gradingRepository;

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
     * Retrieves the names of external code assignments associated with the given course.
     * <p>
     * This method fetches assignment names for the course with the same title as the course
     * identified by {@code courseId}. The current user must have administrator access to the course.
     * </p>
     *
     * @param courseId    the UUID of the course for which to retrieve assignment names
     * @param currentUser the user requesting the information; must have administrator access to the course
     * @return a list of assignment names for the course, or an empty list if access is denied
     *         or an error occurs during the process
     */
    public List<String> getExternalCodeAssignments(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
            String courseTitle = courseServiceClient.queryCourseById(courseId).getTitle();
            return externalCodeAssignmentRepository.findAssignmentNamesByCourseTitle(courseTitle);
        } catch (Exception e) {
            log.error("Failed to get external code assignments for course {}: {}", courseId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Creates and persists a new assignment linked to the given assessment and course.
     * <p>
     * If the assignment type is {@link AssignmentType#CODE_ASSIGNMENT}, the method additionally enriches
     * the assignment with metadata from an external provider (e.g., GitHub Classroom), such as due date,
     * links, and README content.
     * </p>
     *
     * @param courseId              the ID of the course to which the assignment belongs
     * @param assessmentId          the ID of the assessment the assignment is linked to
     * @param createAssignmentInput the input data for creating the assignment
     * @param currentUser           the currently logged-in user; must have access to the course
     * @return the created assignment as a DTO
     * @throws ValidationException if the input is invalid
     * @throws IllegalStateException if the assignment type is CODE_ASSIGNMENT and the enrichment from the external provider fails
     */

    public Assignment createAssignment(final UUID courseId, final UUID assessmentId, final CreateAssignmentInput createAssignmentInput, final LoggedInUser currentUser) {
        assignmentValidator.validateCreateAssignmentInput(createAssignmentInput);

        final AssignmentEntity mappedAssignmentEntity = assignmentMapper.createAssignmentInputToEntity(createAssignmentInput);
        mappedAssignmentEntity.setAssessmentId(assessmentId);
        mappedAssignmentEntity.setCourseId(courseId);

        if (createAssignmentInput.getAssignmentType() == AssignmentType.CODE_ASSIGNMENT) {
            this.createCodeAssignment(courseId, assessmentId, mappedAssignmentEntity, currentUser);
        }

        final AssignmentEntity savedAssignmentEntity = assignmentRepository.save(mappedAssignmentEntity);
        return assignmentMapper.assignmentEntityToDto(savedAssignmentEntity);
    }

    private void createCodeAssignment (final UUID courseId, final UUID assessmentId, final AssignmentEntity assignmentEntity, final LoggedInUser currentUser){
        try {
            String courseTitle = courseServiceClient.queryCourseById(courseId).getTitle();

            String assignmentName = contentServiceClient.queryContentsOfCourse(currentUser.getId(), courseId).stream()
                    .filter(content -> content.getId().equals(assessmentId))
                    .map(content -> content.getMetadata().getName())
                    .findFirst().orElseThrow(() -> new EntityNotFoundException("Content with assessmentId %s not found".formatted(assessmentId)));

            // external assignment must be synced already before the next line happens
            ExternalCodeAssignmentEntity externalAssignment = externalCodeAssignmentRepository.findById(new ExternalCodeAssignmentEntity.PrimaryKey(courseTitle, assignmentName))
                    .orElseThrow(() -> new EntityNotFoundException("External assignment with assessmentId %s not found".formatted(assessmentId)));

            assignmentEntity.setDate(externalAssignment.getDueDate());

            CodeAssignmentMetadataEntity metadata = CodeAssignmentMetadataEntity.builder()
                    .assignment(assignmentEntity)
                    .assignmentLink(externalAssignment.getAssignmentLink())
                    .invitationLink(externalAssignment.getInvitationLink())
                    .readmeHtml(externalAssignment.getReadmeHtml())
                    .build();

            //External Id used to fetch assignment grades
            assignmentEntity.setExternalId(externalAssignment.getExternalId());
            assignmentEntity.setCodeAssignmentMetadata(metadata);
            externalCodeAssignmentRepository.delete(externalAssignment);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enrich code assignment from GitHub Classroom", e);
        }
    }

    /**
     * Synchronizes external code assignments for the specified course using the external code assessment provider.
     * <p>
     * This method:
     * <ul>
     *     <li>Validates that the {@code currentUser} has ADMINISTRATOR access to the specified course.</li>
     *     <li>Invokes {@link CodeAssessmentProvider#syncAssignmentsForCourse(String, LoggedInUser)} to fetch and persist
     *         the latest external assignments (e.g., from GitHub Classroom).</li>
     * </ul>
     * </p>
     *
     * @param courseId    the UUID of the internal MEITREX course whose assignments should be synced
     * @param currentUser the user initiating the sync operation; must have ADMINISTRATOR access to the course
     * @return {@code true} if synchronization was successful, {@code false} if any error occurred (e.g., access denied,
     *         external platform failure, or unexpected exception)
     */
    public boolean syncAssignmentsForCourse(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
            String courseTitle = courseServiceClient.queryCourseById(courseId).getTitle();
            codeAssessmentProvider.syncAssignmentsForCourse(courseTitle, currentUser);
            return true;
        } catch (Exception e) {
            log.error("Failed to sync assignments for course {}: {}", courseId, e.getMessage());
            return false;
        }
    }

    /**
     * Updates the metadata of an existing assignment.
     * <p>
     * Currently, this method allows updating the {@code requiredPercentage} field, which defines the
     * minimum percentage of credits required to successfully complete the assignment.
     * </p>
     * <p>
     *
     * @param assessmentId the ID of the assignment (corresponds to the content ID of the assessment)
     * @param input        the new data to apply to the assignment
     * @param currentUser  the user performing the update; must be an ADMINISTRATOR of the course
     * @return the updated {@link Assignment} DTO
     * @throws EntityNotFoundException if the assignment does not exist
     * @throws ValidationException     if the user lacks required access rights
     */
    public Assignment updateAssignment(UUID assessmentId,
                                       UpdateAssignmentInput input,
                                       LoggedInUser currentUser) {
        AssignmentEntity assignment = assignmentRepository.findById(assessmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, assignment.getCourseId());

        if (input.getRequiredPercentage() != null) {
            assignment.setRequiredPercentage(input.getRequiredPercentage());
        }

        assignmentRepository.save(assignment);
        return assignmentMapper.assignmentEntityToDto(assignment);
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

        final double achievedCredits = input.getAchievedCredits();
        final double totalCredits = assignmentEntity.getTotalCredits();

        final boolean success = achievedCredits >= requiredPercentage * totalCredits;
        final double correctness = totalCredits == 0 ? 1.0f : achievedCredits / totalCredits;

        // create Responses for each exercise and subexercise
        final List<Response> responses = new ArrayList<>();
        for (final ExerciseCompletedInput exerciseCompletedInput: input.getCompletedExercises()) {
            final UUID exerciseId = exerciseCompletedInput.getItemId();
            final ExerciseEntity exerciseEntity = findExerciseEntityInAssignmentEntity(exerciseId, assignmentEntity);
            final double totalExerciseCredits = exerciseEntity.getTotalExerciseCredits();
            final float achievedExercisePercentage = totalExerciseCredits == 0 ? 1.0f : (float) (exerciseCompletedInput.getAchievedCredits() / totalExerciseCredits);
            final Response exerciseResponse = new Response(exerciseId, achievedExercisePercentage);
            responses.add(exerciseResponse);

            for (final SubexerciseCompletedInput subexerciseCompletedInput: exerciseCompletedInput.getCompletedSubexercises()) {
                final UUID subexerciseId = subexerciseCompletedInput.getItemId();
                final SubexerciseEntity subexerciseEntity = findSubexerciseEntityInExerciseEntity(subexerciseId, exerciseEntity);
                final double totalSubexerciseCredits = subexerciseEntity.getTotalSubexerciseCredits();
                final float achievedSubexercisePercentage = totalSubexerciseCredits == 0 ? 1.0f : (float) (subexerciseCompletedInput.getAchievedCredits() / totalSubexerciseCredits);
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
    @Transactional
    public void deleteAssignmentIfContentIsDeleted(final ContentChangeEvent dto) throws IncompleteEventMessageException {

        // validate event message
        checkCompletenessOfDto(dto);

        // only consider DELETE Operations
        if (!dto.getOperation().equals(CrudOperation.DELETE) || dto.getContentIds().isEmpty()) {
            return;
        }

        List<GradingEntity> gradings = gradingRepository.findAllByPrimaryKey_AssessmentIdIn(dto.getContentIds());
        gradingRepository.deleteAll(gradings);
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

    /**
     * Retrieves the external course details (title and URL) for a given internal course ID.
     * <p>
     * If the external course is not already cached in the local database, it attempts to fetch it
     * from the external code assessment provider (e.g., GitHub Classroom) and saves it.
     * </p>
     *
     * @param courseId     the internal UUID of the MEITREX course.
     * @param currentUser  the currently logged-in user, whose role will be validated.
     * @return the {@link ExternalCourse} if found and accessible, or {@code null} if:
     *         <ul>
     *             <li>the user does not have ADMINISTRATOR access to the course</li>
     *             <li>an error occurs while contacting the external platform or user service</li>
     *             <li>no such external course can be resolved</li>
     *         </ul>
     */

    public ExternalCourse getExternalCourse(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);

            String courseTitle = courseServiceClient.queryCourseById(courseId).getTitle();

            ExternalCourseEntity entity = externalCourseRepository.findById(courseTitle)
                    .orElseGet(() -> {
                        try {
                            ExternalCourse external = codeAssessmentProvider.getExternalCourse(courseTitle, currentUser);
                            ExternalCourseEntity newEntity = new ExternalCourseEntity(courseTitle, external.getUrl());
                            return externalCourseRepository.save(newEntity);
                        } catch (ExternalPlatformConnectionException | UserServiceConnectionException e) {
                            return null;
                        }
                    });

            if (entity == null) return null;

            return new ExternalCourse(entity.getCourseTitle(), entity.getUrl());

        } catch (Exception e) {
            log.error("Failed to get external course for course {}: {}", courseId, e.getMessage(), e);
            return null;
        }
    }


}
