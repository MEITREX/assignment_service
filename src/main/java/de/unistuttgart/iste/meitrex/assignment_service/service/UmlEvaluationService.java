package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlFeedbackEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UmlEvaluationService {

    private final UmlStudentSolutionRepository solutionRepository;

    @Async
    @Transactional
    public void generateFeedbackAsync(final UUID solutionId, final String diagram) {
        log.info("Starting background feedback generation for solution {}", solutionId);

        try {
            Thread.sleep(20000);
            String feedbackText = "Test feedback text";

            UmlStudentSolutionEntity solution = solutionRepository.findById(solutionId)
                    .orElseThrow();

            UmlFeedbackEntity feedback = UmlFeedbackEntity.builder()
                    .solution(solution)
                    .comment(feedbackText)
                    .points(8)
                    .build();

            solution.setFeedback(feedback);

            log.info("Feedback successfully saved for solution {}", solutionId);

        } catch (InterruptedException e) {
            log.error("Async feedback generation interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error during async feedback generation", e);
        }
    }

    @Transactional
    public void generateFeedback(final UmlStudentSolutionEntity solution, final String semanticModel) {
        String feedbackText = "Manual Test feedback text";

        UmlFeedbackEntity feedback = UmlFeedbackEntity.builder()
            .solution(solution)
            .comment(feedbackText)
            .points(8)
            .build();

        solution.setFeedback(feedback);
    }
}
