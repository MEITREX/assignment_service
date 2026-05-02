package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.UmlExerciseMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlEvaluationJobRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlExerciseRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSubmissionRepository;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.UmlDiagramInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UmlExerciseServiceQueueIntegrationTest {

    @Mock
    private UmlExerciseRepository exerciseRepository;

    @Mock
    private UmlStudentSubmissionRepository submissionRepository;

    @Mock
    private UmlStudentSolutionRepository solutionRepository;

    @Mock
    private UmlEvaluationJobRepository jobRepository;

    @Mock
    private UmlExerciseMapper umlMapper;

    @Mock
    private ContentServiceClient contentServiceClient;

    @Mock
    private UmlEvaluationService evaluationService;

    @Mock
    private UmlEvaluationQueueService queueService;

    private UmlExerciseService exerciseService;

    private UUID courseId;
    private UUID assessmentId;
    private UUID studentId;
    private UUID exerciseId;
    private UUID submissionId;
    private UUID solutionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exerciseService = new UmlExerciseService(
            exerciseRepository,
            submissionRepository,
            solutionRepository,
            umlMapper,
            contentServiceClient,
            evaluationService,
            queueService
        );

        courseId = UUID.randomUUID();
        assessmentId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        exerciseId = UUID.randomUUID();
        submissionId = UUID.randomUUID();
        solutionId = UUID.randomUUID();
    }

    @Test
    void testSaveStudentSolution_WithSubmitTrue_CreatesEvaluationJob() {
        // Arrange
        UmlExerciseEntity exercise = UmlExerciseEntity.builder()
            .id(exerciseId)
            .assessmentId(assessmentId)
            .courseId(courseId)
            .studentSubmissions(new ArrayList<>())
            .build();

        UmlStudentSubmissionEntity submission = UmlStudentSubmissionEntity.builder()
            .id(submissionId)
            .studentId(studentId)
            .exercise(exercise)
            .solutions(new ArrayList<>())
            .build();

        UmlStudentSolutionEntity solution = UmlStudentSolutionEntity.builder()
            .id(solutionId)
            .submission(submission)
            .diagram(UmlDiagram.builder().semanticModel("model").build())
            .build();

        UmlDiagramInput diagramInput = new UmlDiagramInput("code", "model");

        exercise.getStudentSubmissions().add(submission);
        submission.getSolutions().add(solution);

        when(exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId))
            .thenReturn(Optional.of(exercise));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(UmlStudentSolutionEntity.class)))
            .thenReturn(solution);
        when(queueService.createJob(any(UmlStudentSolutionEntity.class)))
            .thenReturn(UmlEvaluationJobEntity.builder()
                .id(UUID.randomUUID())
                .solution(solution)
                .status(UmlEvaluationJobStatus.ENQUEUED)
                .build());

        // Act
        exerciseService.saveStudentSolution(assessmentId, studentId, diagramInput, solutionId, true);

        // Assert
        verify(queueService, times(1)).createJob(any(UmlStudentSolutionEntity.class));
        verify(evaluationService, never()).generateFeedback(any(), any(), any(), anyInt(), anyDouble(), anyBoolean());
    }

    @Test
    void testSaveStudentSolution_WithSubmitFalse_DoesNotCreateEvaluationJob() {
        // Arrange
        UmlExerciseEntity exercise = UmlExerciseEntity.builder()
            .id(exerciseId)
            .assessmentId(assessmentId)
            .courseId(courseId)
            .studentSubmissions(new ArrayList<>())
            .build();

        UmlStudentSubmissionEntity submission = UmlStudentSubmissionEntity.builder()
            .id(submissionId)
            .studentId(studentId)
            .exercise(exercise)
            .solutions(new ArrayList<>())
            .build();

        UmlStudentSolutionEntity solution = UmlStudentSolutionEntity.builder()
            .id(solutionId)
            .submission(submission)
            .diagram(UmlDiagram.builder().semanticModel("model").build())
            .build();

        UmlDiagramInput diagramInput = new UmlDiagramInput("code", "model");

        exercise.getStudentSubmissions().add(submission);
        submission.getSolutions().add(solution);

        when(exerciseRepository.findByAssessmentIdWithSubmissions(assessmentId))
            .thenReturn(Optional.of(exercise));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(UmlStudentSolutionEntity.class)))
            .thenReturn(solution);

        // Act
        exerciseService.saveStudentSolution(assessmentId, studentId, diagramInput, solutionId, false);

        // Assert
        verify(queueService, never()).createJob(any(UmlStudentSolutionEntity.class));
    }
}
