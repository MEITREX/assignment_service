package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSubmissionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.UmlExerciseMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlExerciseRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import static de.unistuttgart.iste.meitrex.common.user_handling.UserCourseAccessValidator.validateUserHasAccessToCourse;
import de.unistuttgart.iste.meitrex.generated.dto.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmlExerciseService {

    private final UmlExerciseRepository exerciseRepository;
    private final UmlStudentSubmissionRepository submissionRepository;
    private final UmlStudentSolutionRepository solutionRepository;
    private final UmlExerciseMapper umlMapper;


    private final UmlEvaluationService evaluationService;

    /**
     * Fetches the full UML exercise details by its assessment ID.
     */
    public UmlExercise getExerciseByAssessmentId(UUID assessmentId) {
        return exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .map(umlMapper::entityToDto)
            .orElseThrow(() -> new EntityNotFoundException("UmlExercise not found for assessmentId: " + assessmentId));
    }

    /**
     * Creates a new UML exercise after the assignment was created
     */
    public UmlExercise createExercise(final UUID courseId, UUID assessmentId, final CreateUmlExerciseInput input) {
        UmlExerciseEntity entity = UmlExerciseEntity.builder()
            .assessmentId(assessmentId)
            .courseId(courseId)
            .description(input.getDescription())
            .showSolution(input.getShowSolution())
            .totalPoints(input.getTotalPoints())
            .requiredPercentage(input.getRequiredPercentage())
            .studentSubmissions(new ArrayList<>())
            .build();

        UmlExerciseEntity savedEntity = exerciseRepository.save(entity);
        return umlMapper.entityToDto(savedEntity);
    }

    /**
     * Initializes a mutation object for a UML exercise and checks permissions.
     */
    public UmlExerciseMutation mutateUmlExercise(final UUID assessmentId, final LoggedInUser currentUser) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, entity.getCourseId());

        return new UmlExerciseMutation(assessmentId);
    }

    /**
     * Updates the reference solution for a task.
     */
    public UmlExercise updateTutorSolution(final UUID assessmentId, final String tutorSolution) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        entity.setTutorSolution(tutorSolution);
        UmlExerciseEntity savedEntity = exerciseRepository.save(entity);
        return umlMapper.entityToDto(savedEntity);
    }

    /**
     * Submits a new diagram as a student solution attempt.
     */
    public UmlStudentSolution submitSolution(final UUID assessmentId, final UUID studentId, final String diagram) {
        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        UmlStudentSubmissionEntity submission = submissionRepository
            .findByStudentAndAssessmentWithSolutions(studentId, exercise.getId())
            .orElseGet(() -> submissionRepository.save(UmlStudentSubmissionEntity.builder()
                .studentId(studentId)
                .exercise(exercise)
                .solutions(new ArrayList<>())
                .build()));

        UmlStudentSolutionEntity solutionEntity = UmlStudentSolutionEntity.builder()
            .submission(submission)
            .diagram(diagram)
            .submittedAt(OffsetDateTime.now())
            .build();

        submission.getSolutions().add(solutionEntity);
        UmlStudentSolutionEntity savedEntity = solutionRepository.save(solutionEntity);

        evaluationService.generateFeedbackAsync(savedEntity.getId(), diagram);

        log.info("Solution {} submitted. Feedback generation started in background.", savedEntity.getId());

        return umlMapper.solutionEntityToDto(savedEntity);
    }
}
