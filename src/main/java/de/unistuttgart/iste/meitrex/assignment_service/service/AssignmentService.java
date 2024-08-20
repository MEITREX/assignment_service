package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public Assignment createAssignment(final UUID courseId, final UUID assessmentId, final CreateAssignmentInput createAssignmentInput) {
        assignmentValidator.validateCreateAssignmentInput(createAssignmentInput);

        final AssignmentEntity mappedAssignmentEntity = assignmentMapper.createAssignmentInputToEntity(createAssignmentInput);
        mappedAssignmentEntity.setAssessmentId(assessmentId);
        mappedAssignmentEntity.setCourseId(courseId);

        final AssignmentEntity savedAssignmentEntity = assignmentRepository.save(mappedAssignmentEntity);
        return assignmentMapper.assignmentEntityToDto(savedAssignmentEntity);
    }

    public AssignmentMutation mutateAssignment(final UUID assessmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignmentEntity = this.requireAssignmentExists(assessmentId);

        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, assignmentEntity.getCourseId());

        // this is basically an empty object, only serving as a parent for the nested mutations
        return new AssignmentMutation(assessmentId);
    }

    public AssignmentCompletedFeedback logAssignmentCompleted(final LogAssignmentCompletedInput input, final LoggedInUser currentUser) {
        final AssignmentEntity assignmentEntity = this.requireAssignmentExists(input.getAssessmentId());

        // TODO adjust required role during further development
        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.TUTOR, assignmentEntity.getCourseId());

        return this.publishProgress(input, currentUser.getId());
    }

    protected AssignmentCompletedFeedback publishProgress(final LogAssignmentCompletedInput input, final UUID userId) {
        final AssignmentEntity assignmentEntity = requireAssignmentExists(input.getAssessmentId());

        // TODO is "updateExerciseStatistics(input, userId, assignmentEntity)" needed?

        final double achievedCredits = input.getAchievedCredits();
        final double totalCredits = assignmentEntity.getTotalCredits();

        final boolean success = achievedCredits >= 0.5 * totalCredits;
        final double correctness = achievedCredits / totalCredits;

        // create new user progress event message
        final ContentProgressedEvent userProgressLogEvent = ContentProgressedEvent.builder()
                .userId(userId)
                .contentId(assignmentEntity.getAssessmentId())
                .hintsUsed(0)
                .success(success)
                .timeToComplete(null)
                .correctness(correctness)
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
