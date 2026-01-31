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
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
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
    private static final String DEFAULT_START_DIAGRAM = """
        classDiagram {
            class("HelloWorld") {
                public {
                    hello : string
                }
            }
        }
    """;

    /**
     * Fetches the full UML exercise details by its assessment ID.
     */
    public UmlExercise getExerciseByAssessmentId(UUID assessmentId) {
        return exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .map(umlMapper::entityToDto)
            .orElseThrow(() -> new EntityNotFoundException("UmlExercise not found for assessmentId: " + assessmentId));
    }

    /**
     * Helper to find or initialize the submission container for a student.
     */
    private UmlStudentSubmissionEntity getOrCreateSubmission(UmlExerciseEntity exercise, UUID studentId) {
        return submissionRepository
            .findByStudentAndAssessmentWithSolutions(studentId, exercise.getId())
            .orElseGet(() -> submissionRepository.save(UmlStudentSubmissionEntity.builder()
                .studentId(studentId)
                .exercise(exercise)
                .solutions(new ArrayList<>())
                .build()));
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
    public UmlExerciseMutation  mutateUmlExercise(final UUID assessmentId, final LoggedInUser currentUser) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.STUDENT, entity.getCourseId());

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
     * Creates a new unsubmitted solution for a student.
     *
     * @param assessmentId       The ID of the exercise.
     * @param studentId          The ID of the student.
     * @param createFromPrevious If true, copies the diagram from the most recent submission.
     * @return The newly created solution DTO.
     */
    @Transactional
    public UmlStudentSolution createNewSolution(UUID assessmentId, UUID studentId, boolean createFromPrevious) {
        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new NoSuchElementException("Exercise not found"));

        UmlStudentSubmissionEntity submission = getOrCreateSubmission(exercise, studentId);

        String diagram;
        if (createFromPrevious) {
            // Find the most recently submitted solution
            diagram = submission.getSolutions().stream()
                .filter(s -> s.getSubmittedAt() != null)
                .max(Comparator.comparing(UmlStudentSolutionEntity::getSubmittedAt))
                .map(UmlStudentSolutionEntity::getDiagram)
                .orElseThrow(() -> new IllegalStateException(
                    "Cannot create from previous: No submitted solutions found for this student."));
        } else {
            diagram = DEFAULT_START_DIAGRAM;
        }

        UmlStudentSolutionEntity newSolution = UmlStudentSolutionEntity.builder()
            .submission(submission)
            .diagram(diagram)
            .submittedAt(null)
            .build();

        UmlStudentSolutionEntity saved = solutionRepository.save(newSolution);
        submission.getSolutions().add(saved);

        return umlMapper.solutionEntityToDto(saved);
    }

    /**
     * Saves or submits a student's solution attempt.
     * Updates an existing draft if a solutionId is provided or an unsubmitted solution exists.
     * Creates a new solution record if no unsubmitted draft is found.
     */
    public UmlStudentSolution saveStudentSolution(final UUID assessmentId,
                                                  final UUID studentId,
                                                  final String diagram,
                                                  @Nullable final UUID solutionId,
                                                  final boolean submit) {
        log.info("saveStudentSolution triggered: assessmentId={}, studentId={}, submit={}",
                assessmentId, studentId, submit);

        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found for assessmentId: " + assessmentId));

        UmlStudentSubmissionEntity submission = getOrCreateSubmission(exercise, studentId);

        UmlStudentSolutionEntity solutionEntity;

        if (solutionId != null) {
            // If a specific solutionId is requested, verify it exists and is still a draft
            solutionEntity = solutionRepository.findById(solutionId)
                    .orElseThrow(() -> new IllegalArgumentException("Solution not found for id: " + solutionId));

            if (solutionEntity.getSubmittedAt() != null) {
                throw new IllegalStateException("Cannot modify a solution that has already been submitted.");
            }
        } else {
            // Otherwise, look for an existing unsubmitted draft in the container
            solutionEntity = submission.getSolutions().stream()
                .filter(s -> s.getSubmittedAt() == null)
                .findFirst()
                .orElseGet(() -> {
                    // No current draft exists; create a new solution record
                    UmlStudentSolutionEntity newSolution = UmlStudentSolutionEntity.builder()
                        .submission(submission)
                        .diagram(diagram)
                        .build();
                    submission.getSolutions().add(newSolution);
                    return newSolution;
                });
        }

        solutionEntity.setDiagram(diagram);

        if (submit) {
            solutionEntity.setSubmittedAt(OffsetDateTime.now());
        }

        UmlStudentSolutionEntity savedEntity = solutionRepository.save(solutionEntity);

        if (submit) {
            evaluationService.generateFeedbackAsync(savedEntity.getId(), diagram);
            log.info("Solution submitted and evaluation triggered for id: {}", savedEntity.getId());
        } else {
            log.info("Draft saved for solution id: {}", savedEntity.getId());
        }

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
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(exerciseDto.getAssessmentId())
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
