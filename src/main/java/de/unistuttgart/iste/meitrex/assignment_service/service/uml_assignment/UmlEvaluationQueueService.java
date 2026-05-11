package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlEvaluationJobEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlEvaluationJobStatus;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlEvaluationJobRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UmlEvaluationQueueService {

    private final UmlEvaluationJobRepository jobRepository;
    private final UmlStudentSolutionRepository solutionRepository;
    private final UmlEvaluationService evaluationService;
    private final TopicPublisher topicPublisher;

    /**
     * Polls the database every 500ms for ENQUEUED jobs and processes them.
     * Also performs crash recovery: if any job is in PROCESSING state on startup,
     * it gets reset to ENQUEUED to retry.
     */
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndProcessJobs() {
        // Crash recovery: if any job is PROCESSING, reset it to ENQUEUED
        // This handles the case where the service crashed while processing
        try {
            long processingCount = jobRepository.findAllByStatus(UmlEvaluationJobStatus.PROCESSING).stream()
                .filter(job -> {
                    // Only reset if it's been processing for more than 10 minutes (likely crashed)
                    if (job.getStartedAt() != null) {
                        OffsetDateTime tenMinutesAgo = OffsetDateTime.now().minusMinutes(10);
                        return job.getStartedAt().isBefore(tenMinutesAgo);
                    }
                    return false;
                })
                .peek(job -> {
                    log.warn("Resetting stale job {} to ENQUEUED (was PROCESSING for > 10 minutes)", job.getId());
                    job.setStatus(UmlEvaluationJobStatus.ENQUEUED);
                    job.setStartedAt(null);
                    jobRepository.save(job);
                })
                .count();
            
            if (processingCount > 0) {
                log.info("Reset {} stale PROCESSING jobs to ENQUEUED", processingCount);
            }
        } catch (Exception e) {
            log.error("Error during crash recovery", e);
        }

        // Process one ENQUEUED job
        Optional<UmlEvaluationJobEntity> job = jobRepository.findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus.ENQUEUED);
        
        if (job.isPresent()) {
            processJob(job.get());
        }
    }

    /**
     * Processes a single evaluation job asynchronously.
     * Transitions: ENQUEUED → PROCESSING → DONE/FAILED
     */
    private void processJob(UmlEvaluationJobEntity job) {
        try {
            // Mark as PROCESSING
            job.setStatus(UmlEvaluationJobStatus.PROCESSING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.save(job);

            // Process asynchronously so polling can continue
            executeEvaluationAsync(job.getId());
        } catch (Exception e) {
            log.error("Error starting async evaluation for job {}", job.getId(), e);
            job.setStatus(UmlEvaluationJobStatus.FAILED);
            job.setErrorMessage("Failed to start async evaluation: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }

    /**
     * Executes the evaluation asynchronously.
     * This runs in a separate thread pool so polling can continue.
     */
    @Async
    @Transactional
    public void executeEvaluationAsync(UUID jobId) {
        Optional<UmlEvaluationJobEntity> jobOpt = jobRepository.findById(jobId);
        
        if (jobOpt.isEmpty()) {
            log.error("Job not found: {}", jobId);
            return;
        }

        UmlEvaluationJobEntity job = jobOpt.get();
        UmlStudentSolutionEntity solution = job.getSolution();

        try {
            // Refresh solution entity to get exercise details
            Optional<UmlStudentSolutionEntity> solutionOpt = solutionRepository.findById(solution.getId());
            if (solutionOpt.isEmpty()) {
                throw new IllegalStateException("Solution not found: " + solution.getId());
            }

            solution = solutionOpt.get();
            
            // Get the exercise through submission
            if (solution.getSubmission() == null) {
                throw new IllegalStateException("Solution submission is missing");
            }

            // Call evaluation service - this performs the actual LLM evaluation
            evaluationService.generateFeedbackForJob(solution, job);

            // Mark job as DONE
            job.setStatus(UmlEvaluationJobStatus.DONE);
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            log.info("Evaluation completed successfully for job {}", jobId);

        } catch (Exception e) {
            log.error("Error evaluating solution in job {}", jobId, e);
            job.setStatus(UmlEvaluationJobStatus.FAILED);
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error");
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            // Send failure notification to the student
            try {
                if (solution.getSubmission() != null && solution.getSubmission().getExercise() != null) {
                    UUID studentUserId = solution.getSubmission().getStudentId();
                    UUID courseId = solution.getSubmission().getExercise().getCourseId();
                    UUID assessmentId = solution.getSubmission().getExercise().getAssessmentId();
                    String link = "/courses/" + courseId + "/uml/" + assessmentId;
                    topicPublisher.notificationEvent(
                        courseId,
                        java.util.List.of(studentUserId),
                        ServerSource.COURSE,
                        link,
                        "Your UML submission evaluation failed",
                        "Automated evaluation failed. Please try again or contact your instructor."
                    );
                }
            } catch (Exception ne) {
                log.error("Failed to send failure notification for job {}", jobId, ne);
            }
        }
    }

    /**
     * Creates an evaluation job for the given solution.
     * This is called when a student submits a solution.
     */
    @Transactional
    public UmlEvaluationJobEntity createJob(UmlStudentSolutionEntity solution) {
        UmlEvaluationJobEntity job = UmlEvaluationJobEntity.builder()
            .solution(solution)
            .status(UmlEvaluationJobStatus.ENQUEUED)
            .createdAt(OffsetDateTime.now())
            .build();

        return jobRepository.save(job);
    }

    /**
     * Gets the current status of an evaluation for a solution.
     */
    public UmlEvaluationJobStatus getJobStatus(UUID solutionId) {
        Optional<UmlEvaluationJobEntity> job = jobRepository.findBySolutionId(solutionId);
        return job.map(UmlEvaluationJobEntity::getStatus).orElse(null);
    }

    /**
     * Gets the error message for a failed evaluation.
     */
    public String getJobErrorMessage(UUID solutionId) {
        Optional<UmlEvaluationJobEntity> job = jobRepository.findBySolutionId(solutionId);
        return job.map(UmlEvaluationJobEntity::getErrorMessage).orElse(null);
    }
}
