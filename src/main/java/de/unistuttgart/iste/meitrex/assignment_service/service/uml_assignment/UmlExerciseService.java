package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlDiagram;
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
        UmlDiagram tutorSolution = input.getTutorSolution() != null
                ? umlMapper.inputToEntity(input.getTutorSolution())
                : UmlDiagram.builder().diagramCode("").semanticModel("").build();

        UmlExerciseEntity entity = UmlExerciseEntity.builder()
                .assessmentId(assessmentId)
                .courseId(courseId)
                .description(input.getDescription())
                .showSolution(input.getShowSolution())
                .totalPoints(input.getTotalPoints())
                .requiredPercentage(input.getRequiredPercentage())
                .tutorSolution(tutorSolution)
                .studentSubmissions(new ArrayList<>())
                .build();

        return umlMapper.entityToDto(exerciseRepository.save(entity));
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
    public UmlExercise updateTutorSolution(final UUID assessmentId, final UmlDiagramInput tutorSolution) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        entity.setTutorSolution(umlMapper.inputToEntity(tutorSolution));
        return umlMapper.entityToDto(exerciseRepository.save(entity));
    }

    /**
     * Updates fields of a UML exercise.
     */
    public UmlExercise updateUmlExercise(final UUID assessmentId, final UpdateUmlExerciseInput input) {
        UmlExerciseEntity entity = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));

        if (input.getDescription() != null) {
            entity.setDescription(input.getDescription());
        }

        if (input.getRequiredPercentage() != null) {
            entity.setRequiredPercentage(input.getRequiredPercentage());
        }

        if (input.getShowSolution() != null) {
            entity.setShowSolution(input.getShowSolution());
        }

        if (input.getTutorSolution() != null) {
            entity.setTutorSolution(input.getTutorSolution());
        }

        if (input.getTotalPoints() != null) {
            entity.setTotalPoints(input.getTotalPoints());
        }

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

        boolean hasUnsubmitted = submission.getSolutions().stream()
            .anyMatch(sol -> sol.getSubmittedAt() == null);

        if (hasUnsubmitted) {
            throw new IllegalStateException("An unsubmitted draft already exists.");
        }

        UmlDiagram diagram;
        if (createFromPrevious) {
            // Find the most recently submitted solution
            diagram = submission.getSolutions().stream()
                .filter(s -> s.getSubmittedAt() != null)
                .max(Comparator.comparing(UmlStudentSolutionEntity::getSubmittedAt))
                .map(UmlStudentSolutionEntity::getDiagram)
                .orElseThrow(() -> new IllegalStateException("No previous submission found."));
        } else {
            diagram = UmlDiagram.builder().diagramCode(DEFAULT_START_DIAGRAM).semanticModel("").build();
        }

        UmlStudentSolutionEntity newSolution = UmlStudentSolutionEntity.builder()
            .submission(submission)
            .diagram(diagram)
            .build();

        return umlMapper.solutionEntityToDto(solutionRepository.save(newSolution));
    }

    /**
     * Saves or submits a student's solution attempt.
     * Updates an existing draft if a solutionId is provided or an unsubmitted solution exists.
     * Creates a new solution record if no unsubmitted draft is found.
     */
    @Transactional
    public UmlStudentSolution saveStudentSolution(
            final UUID assessmentId,
            final UUID studentId,
            final UmlDiagramInput diagramInput,
            @Nullable final UUID solutionId,
            final boolean submit
    ) {
        UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Exercise not found."));

        UmlStudentSubmissionEntity submission = getOrCreateSubmission(exercise, studentId);
        UmlStudentSolutionEntity solutionEntity;

        if (solutionId != null) {
            solutionEntity = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new IllegalArgumentException("Solution not found."));

            if (solutionEntity.getSubmittedAt() != null) {
                throw new IllegalStateException("Solution already submitted.");
            }
        } else {
            solutionEntity = submission.getSolutions().stream()
                .filter(s -> s.getSubmittedAt() == null)
                .findFirst()
                .orElseGet(() -> {
                    UmlStudentSolutionEntity newSolution = UmlStudentSolutionEntity.builder()
                        .submission(submission)
                        // Initialize with provided diagram
                        .diagram(umlMapper.inputToEntity(diagramInput))
                        .build();
                    submission.getSolutions().add(newSolution);
                    return newSolution;
                });
        }

        solutionEntity.setDiagram(umlMapper.inputToEntity(diagramInput));

        if (submit) {
            solutionEntity.setSubmittedAt(OffsetDateTime.now());
        }

        UmlStudentSolutionEntity savedEntity = solutionRepository.save(solutionEntity);
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
                 .sorted(Comparator.comparing(
                     UmlStudentSolutionEntity::getSubmittedAt,
                     Comparator.nullsLast(Comparator.naturalOrder())
                 ))
                 .map(umlMapper::solutionEntityToDto)
                 .toList())
             .orElse(Collections.emptyList());
     }

    /**
     * Triggers an automated evaluation and feedback generation for a student's most recent submitted solution.
     * <p>
     * This method retrieves the student's latest submitted solution and compares its stored semantic model
     * against the tutor's reference model using a two-step LLM evaluation process. The resulting
     * feedback and points are then persisted directly to the solution entity.
     *
     * @param assessmentId The external UUID of the UML assessment/exercise.
     * @param studentId    The UUID of the student whose submission is being evaluated.
     * @return The updated {@link UmlStudentSolution} DTO, now containing the generated feedback and points.
     * @throws NoSuchElementException If the exercise or submission container cannot be found.
     * @throws IllegalStateException  If no submitted solutions exist, or if the tutor has not
     * yet provided a reference semantic model for comparison.
     */
     @Transactional
     public UmlStudentSolution evaluateLatestSolution(final UUID assessmentId, final UUID studentId) {
         UmlExerciseEntity exercise = exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId)
             .orElseThrow(() -> new NoSuchElementException("Exercise not found"));

         UmlStudentSubmissionEntity submission = exercise.getStudentSubmissions().stream()
             .filter(sub -> sub.getStudentId().equals(studentId))
             .findFirst()
             .orElseThrow(() -> new IllegalStateException("No submission found."));

         UmlStudentSolutionEntity latestSolution = submission.getSolutions().stream()
             .filter(sol -> sol.getSubmittedAt() != null)
             .max(Comparator.comparing(UmlStudentSolutionEntity::getSubmittedAt))
             .orElseThrow(() -> new IllegalStateException("No submitted solutions found."));

         // Check if tutor solution exists
         if (exercise.getTutorSolution() == null || exercise.getTutorSolution().getSemanticModel() == null) {
             throw new IllegalStateException("Tutor solution is missing semantic model for evaluation.");
         }

         evaluationService.generateFeedback(
             latestSolution,
             exercise.getTutorSolution().getSemanticModel(),
             "", // TODO: Add grading rules
             exercise.getTotalPoints()
         );

         return umlMapper.solutionEntityToDto(latestSolution);
     }
}
