package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlEvaluationJobRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UmlEvaluationQueueServiceTest {

    @Mock
    private UmlEvaluationJobRepository jobRepository;

    @Mock
    private UmlStudentSolutionRepository solutionRepository;

    @Mock
    private UmlEvaluationService evaluationService;

    @Mock
    private de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher topicPublisher;

    private UmlEvaluationQueueService queueService;
    private UUID solutionId;
    private UUID jobId;
    private UmlStudentSolutionEntity mockSolution;
    private UmlEvaluationJobEntity mockJob;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        queueService = new UmlEvaluationQueueService(jobRepository, solutionRepository, evaluationService, topicPublisher);

        solutionId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        mockSolution = UmlStudentSolutionEntity.builder()
            .id(solutionId)
            .build();
        
        // ensure submission is present to avoid IllegalState checks
        UmlStudentSubmissionEntity submission = UmlStudentSubmissionEntity.builder()
            .studentId(UUID.randomUUID())
            .build();
        mockSolution.setSubmission(submission);
        mockJob = UmlEvaluationJobEntity.builder()
            .id(jobId)
            .solution(mockSolution)
            .status(UmlEvaluationJobStatus.ENQUEUED)
            .createdAt(OffsetDateTime.now())
            .build();
    }

    @Test
    void testCreateJob_CreatesJobWithEnqueuedStatus() {
        // Arrange
        when(jobRepository.save(any(UmlEvaluationJobEntity.class))).thenReturn(mockJob);

        // Act
        UmlEvaluationJobEntity result = queueService.createJob(mockSolution);

        // Assert
        assertNotNull(result);
        assertEquals(UmlEvaluationJobStatus.ENQUEUED, result.getStatus());
        assertEquals(solutionId, result.getSolution().getId());
        verify(jobRepository, times(1)).save(any(UmlEvaluationJobEntity.class));
    }

    @Test
    void testPollAndProcessJobs_ProcessesEnqueuedJob() {
        // Arrange
        when(jobRepository.findAllByStatus(UmlEvaluationJobStatus.PROCESSING))
            .thenReturn(new ArrayList<>());
        when(jobRepository.findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED))
            .thenReturn(Optional.of(mockJob));
        when(jobRepository.save(any(UmlEvaluationJobEntity.class))).thenReturn(mockJob);

        // Act
        queueService.pollAndProcessJobs();

        // Assert
        verify(jobRepository, times(1)).findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED);
        verify(jobRepository, atLeast(1)).save(any(UmlEvaluationJobEntity.class));
    }

    @Test
    void testPollAndProcessJobs_CrashRecovery_ResetsStaleProcessingJobs() {
        // Arrange
        UmlEvaluationJobEntity staleJob = UmlEvaluationJobEntity.builder()
            .id(UUID.randomUUID())
            .solution(mockSolution)
            .status(UmlEvaluationJobStatus.PROCESSING)
            .startedAt(OffsetDateTime.now().minusMinutes(11)) // 11 minutes ago - stale
            .build();

        when(jobRepository.findAllByStatus(UmlEvaluationJobStatus.PROCESSING))
            .thenReturn(Collections.singletonList(staleJob));
        when(jobRepository.findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED))
            .thenReturn(Optional.empty());
        when(jobRepository.save(any(UmlEvaluationJobEntity.class))).thenReturn(staleJob);

        // Act
        queueService.pollAndProcessJobs();

        // Assert
        verify(jobRepository, atLeast(1)).save(any(UmlEvaluationJobEntity.class));
        assertEquals(UmlEvaluationJobStatus.ENQUEUED, staleJob.getStatus());
    }

    @Test
    void testPollAndProcessJobs_NoCrashRecovery_DoesNotResetRecentProcessingJobs() {
        // Arrange
        UmlEvaluationJobEntity recentJob = UmlEvaluationJobEntity.builder()
            .id(UUID.randomUUID())
            .solution(mockSolution)
            .status(UmlEvaluationJobStatus.PROCESSING)
            .startedAt(OffsetDateTime.now().minusMinutes(4)) // 4 minutes ago - not stale
            .build();

        when(jobRepository.findAllByStatus(UmlEvaluationJobStatus.PROCESSING))
            .thenReturn(Collections.singletonList(recentJob));
        when(jobRepository.findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED))
            .thenReturn(Optional.empty());

        // Act
        queueService.pollAndProcessJobs();

        // Assert
        assertEquals(UmlEvaluationJobStatus.PROCESSING, recentJob.getStatus()); // Status should not change
    }

    @Test
    void testExecuteEvaluationAsync_SuccessfulEvaluation() {
        // Arrange
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(mockSolution));

        // Act
        queueService.executeEvaluationAsync(jobId);

        // Assert
        verify(evaluationService, times(1)).generateFeedbackForJob(mockSolution, mockJob);
        verify(jobRepository, times(1)).save(mockJob);
        assertEquals(UmlEvaluationJobStatus.DONE, mockJob.getStatus());
        assertNotNull(mockJob.getCompletedAt());
    }

    @Test
    void testExecuteEvaluationAsync_JobNotFound() {
        // Arrange
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // Act & Assert - should not throw, but log error
        assertDoesNotThrow(() -> queueService.executeEvaluationAsync(jobId));
        verify(evaluationService, never()).generateFeedbackForJob(any(), any());
    }

    @Test
    void testExecuteEvaluationAsync_EvaluationFailure() {
        // Arrange
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(solutionRepository.findById(solutionId)).thenReturn(Optional.of(mockSolution));
        doThrow(new RuntimeException("LLM evaluation failed"))
            .when(evaluationService).generateFeedbackForJob(mockSolution, mockJob);

        // Act
        queueService.executeEvaluationAsync(jobId);

        // Assert
        verify(jobRepository, times(1)).save(mockJob);
        assertEquals(UmlEvaluationJobStatus.FAILED, mockJob.getStatus());
        assertNotNull(mockJob.getErrorMessage());
        assertTrue(mockJob.getErrorMessage().contains("LLM evaluation failed"));
        assertNotNull(mockJob.getCompletedAt());
    }

    @Test
    void testGetJobStatus_ReturnsCorrectStatus() {
        // Arrange
        when(jobRepository.findBySolutionId(solutionId)).thenReturn(Optional.of(mockJob));

        // Act
        UmlEvaluationJobStatus status = queueService.getJobStatus(solutionId);

        // Assert
        assertEquals(UmlEvaluationJobStatus.ENQUEUED, status);
    }

    @Test
    void testGetJobStatus_ReturnsNullWhenJobNotFound() {
        // Arrange
        when(jobRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());

        // Act
        UmlEvaluationJobStatus status = queueService.getJobStatus(solutionId);

        // Assert
        assertNull(status);
    }

    @Test
    void testGetJobErrorMessage_ReturnsErrorMessage() {
        // Arrange
        mockJob.setStatus(UmlEvaluationJobStatus.FAILED);
        mockJob.setErrorMessage("Timeout waiting for LLM response");
        when(jobRepository.findBySolutionId(solutionId)).thenReturn(Optional.of(mockJob));

        // Act
        String errorMessage = queueService.getJobErrorMessage(solutionId);

        // Assert
        assertEquals("Timeout waiting for LLM response", errorMessage);
    }

    @Test
    void testGetJobErrorMessage_ReturnsNullWhenJobNotFound() {
        // Arrange
        when(jobRepository.findBySolutionId(solutionId)).thenReturn(Optional.empty());

        // Act
        String errorMessage = queueService.getJobErrorMessage(solutionId);

        // Assert
        assertNull(errorMessage);
    }
}
