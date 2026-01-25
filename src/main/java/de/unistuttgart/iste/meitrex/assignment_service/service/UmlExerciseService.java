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
import java.util.*;

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
            .tutorSolution(input.getTutorSolution())
            .studentSubmissions(new ArrayList<>())
            .build();

        if (entity.getTutorSolution() == null) {
            entity.setTutorSolution("");
        }

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
        log.info("SubmitSolution triggered: assessmentId={}, studentId={}", assessmentId, studentId);

        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found for assessmentId: " + assessmentId));

        log.info("Found Exercise. Internal ID: {}, Assessment ID: {}", exercise.getId(), exercise.getAssessmentId());

        // Check for existing submission
        Optional<UmlStudentSubmissionEntity> submissionOptional = submissionRepository
            .findByStudentAndAssessmentWithSolutions(studentId, exercise.getId());

        UmlStudentSubmissionEntity submission;

        if (submissionOptional.isPresent()) {
            submission = submissionOptional.get();
            log.info("REUSING existing submission: id={}, studentId={}, solutionsCount={}",
                    submission.getId(), submission.getStudentId(), submission.getSolutions().size());
        } else {
            log.info("NOT FOUND: No submission for studentId={} and exerciseId={}. Creating new entity.",
                    studentId, exercise.getId());

            submission = submissionRepository.save(UmlStudentSubmissionEntity.builder()
                .studentId(studentId)
                .exercise(exercise)
                .solutions(new ArrayList<>())
                .build());

            log.info("CREATED new submission: id={}", submission.getId());
        }

        UmlStudentSolutionEntity solutionEntity = UmlStudentSolutionEntity.builder()
            .submission(submission)
            .diagram(diagram)
            .submittedAt(OffsetDateTime.now())
            .build();

        submission.getSolutions().add(solutionEntity);
        UmlStudentSolutionEntity savedEntity = solutionRepository.save(solutionEntity);

        log.info("SAVED new solution: id={}, attached to submission: {}", savedEntity.getId(), submission.getId());

        evaluationService.generateFeedbackAsync(savedEntity.getId(), diagram);

        return umlMapper.solutionEntityToDto(savedEntity);
    }

     /**
     * Retrieves all solution attempts for a specific student associated with a given exercise.
     * <p>
     * The method fetches the exercise entity, filters the student submissions to find the container
     * belonging to the specified student, and returns their solutions sorted by submission date
     * in descending order (newest first).
     *
     * @param exerciseDto The exercise DTO containing the identifier used to fetch the entity.
     * @param studentId   The UUID of the student whose solutions are being requested.
     * @return A list of {@link UmlStudentSolution} DTOs, or an empty list if the student
     * has no submissions for this exercise.
     * @throws NoSuchElementException If no exercise is found for the given identifier.
     */
    public List<UmlStudentSolution> getSolutionsByStudent(final UmlExercise exerciseDto, final UUID studentId) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(exerciseDto.getId())
            .orElseThrow(() -> new NoSuchElementException("Exercise not found"));

        return entity.getStudentSubmissions().stream()
            .filter(sub -> sub.getStudentId().equals(studentId))
            .findFirst()
            .map(sub -> sub.getSolutions().stream()
                .sorted(Comparator.comparing(UmlStudentSolutionEntity::getSubmittedAt).reversed())
                .map(umlMapper::solutionEntityToDto)
                .toList())
            .orElse(Collections.emptyList());
    }

     /**
     * Triggers a manual evaluation and feedback generation for a student's most recent solution attempt.
     * <p>
     * This method identifies the latest solution by finding the student's submission container and
     * selecting the solution with the most recent 'submittedAt' timestamp. It then invokes the
     * evaluation service to attach feedback directly to that solution entity.
     *
     * @param assessmentId  The external UUID of the assessment/exercise.
     * @param studentId     The UUID of the student whose work is being evaluated.
     * @param semanticModel Semantic model data used for evaluation to compare against tutor solution.
     * @return The updated {@link UmlStudentSolution} DTO containing the newly generated feedback.
     * @throws NoSuchElementException If the exercise is not found.
     * @throws IllegalStateException If no submission or no solutions are found for the student,
     * meaning there is nothing to evaluate.
     */
    public UmlStudentSolution evaluateLatestSolution(
            final UUID assessmentId, final UUID studentId, final String semanticModel) {
        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new NoSuchElementException("Exercise not found"));

        UmlStudentSubmissionEntity submission = exercise.getStudentSubmissions().stream()
            .filter(sub -> sub.getStudentId().equals(studentId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No submission found for this student."));

        UmlStudentSolutionEntity latestSolution = submission.getSolutions().stream()
            .max(Comparator.comparing(UmlStudentSolutionEntity::getSubmittedAt))
            .orElseThrow(() -> new IllegalStateException("Submission exists but contains no solutions."));

        evaluationService.generateFeedback(latestSolution, semanticModel);

        return umlMapper.solutionEntityToDto(latestSolution);
    }
}
