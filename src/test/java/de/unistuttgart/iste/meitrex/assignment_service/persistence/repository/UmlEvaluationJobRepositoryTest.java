package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UmlEvaluationJobRepositoryTest {

    @Autowired
    private UmlEvaluationJobRepository jobRepository;

    @Autowired
    private UmlStudentSolutionRepository solutionRepository;

    @Autowired
    private UmlStudentSubmissionRepository submissionRepository;

    @Autowired
    private UmlExerciseRepository exerciseRepository;

    private UmlEvaluationJobEntity createTestJob(UmlEvaluationJobStatus status) {
        // Create exercise
        UmlExerciseEntity exercise = UmlExerciseEntity.builder()
            .assessmentId(UUID.randomUUID())
            .courseId(UUID.randomUUID())
            .description("test exercise")
            .showSolution(false)
            .totalPoints(0)
            .requiredPercentage(0.0)
            .build();
        exerciseRepository.save(exercise);

        // Create submission
        UmlStudentSubmissionEntity submission = UmlStudentSubmissionEntity.builder()
            .studentId(UUID.randomUUID())
            .exercise(exercise)
            .build();
        submissionRepository.save(submission);

        // Create solution
        UmlStudentSolutionEntity solution = UmlStudentSolutionEntity.builder()
            .submission(submission)
            .submittedAt(OffsetDateTime.now())
            .diagram(UmlDiagram.builder().semanticModel("test").build())
            .build();
        solutionRepository.save(solution);

        // Create job
        UmlEvaluationJobEntity job = UmlEvaluationJobEntity.builder()
            .solution(solution)
            .status(status)
            .createdAt(OffsetDateTime.now())
            .build();
        return jobRepository.save(job);
    }

    @Test
    void testFindFirstByStatusOrderByCreatedAt() {
        // Arrange
        UmlEvaluationJobEntity job1 = createTestJob(UmlEvaluationJobStatus.ENQUEUED);
        Thread.yield(); // Ensure different timestamps
        UmlEvaluationJobEntity job2 = createTestJob(UmlEvaluationJobStatus.ENQUEUED);

        // Act
        Optional<UmlEvaluationJobEntity> firstJob = jobRepository
            .findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED);

        // Assert
        assertTrue(firstJob.isPresent());
        assertEquals(job1.getId(), firstJob.get().getId());
    }

    @Test
    void testFindAllByStatus() {
        // Arrange
        createTestJob(UmlEvaluationJobStatus.ENQUEUED);
        createTestJob(UmlEvaluationJobStatus.ENQUEUED);
        createTestJob(UmlEvaluationJobStatus.PROCESSING);

        // Act
        List<UmlEvaluationJobEntity> enqueuedJobs = jobRepository
            .findAllByStatus(UmlEvaluationJobStatus.ENQUEUED);

        // Assert
        assertEquals(2, enqueuedJobs.size());
    }

    @Test
    void testFindBySolutionId() {
        // Arrange
        UmlEvaluationJobEntity job = createTestJob(UmlEvaluationJobStatus.ENQUEUED);

        // Act
        Optional<UmlEvaluationJobEntity> foundJob = jobRepository.findBySolutionId(job.getSolution().getId());

        // Assert
        assertTrue(foundJob.isPresent());
        assertEquals(job.getId(), foundJob.get().getId());
        assertEquals(job.getSolution().getId(), foundJob.get().getSolution().getId());
    }

    @Test
    void testFindBySolutionId_NotFound() {
        // Act
        Optional<UmlEvaluationJobEntity> foundJob = jobRepository
            .findBySolutionId(UUID.randomUUID());

        // Assert
        assertFalse(foundJob.isPresent());
    }

    @Test
    void testJobStatusTransitions() {
        // Arrange
        UmlEvaluationJobEntity job = createTestJob(UmlEvaluationJobStatus.ENQUEUED);

        // Act & Assert - ENQUEUED → PROCESSING
        job.setStatus(UmlEvaluationJobStatus.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        jobRepository.save(job);

        Optional<UmlEvaluationJobEntity> foundJob = jobRepository.findById(job.getId());
        assertTrue(foundJob.isPresent());
        assertEquals(UmlEvaluationJobStatus.PROCESSING, foundJob.get().getStatus());

        // Act & Assert - PROCESSING → DONE
        UmlEvaluationJobEntity updated = foundJob.get();
        updated.setStatus(UmlEvaluationJobStatus.DONE);
        updated.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(updated);

        Optional<UmlEvaluationJobEntity> finalJob = jobRepository.findById(job.getId());
        assertTrue(finalJob.isPresent());
        assertEquals(UmlEvaluationJobStatus.DONE, finalJob.get().getStatus());
        assertNotNull(finalJob.get().getCompletedAt());
    }

    @Test
    void testJobErrorMessage() {
        // Arrange
        UmlEvaluationJobEntity job = createTestJob(UmlEvaluationJobStatus.FAILED);
        job.setErrorMessage("LLM timeout after 30 seconds");
        jobRepository.save(job);

        // Act
        Optional<UmlEvaluationJobEntity> foundJob = jobRepository.findById(job.getId());

        // Assert
        assertTrue(foundJob.isPresent());
        assertEquals("LLM timeout after 30 seconds", foundJob.get().getErrorMessage());
        assertEquals(UmlEvaluationJobStatus.FAILED, foundJob.get().getStatus());
    }
}
