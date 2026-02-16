package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlFeedbackEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UmlStudentSolutionRepository;
import de.unistuttgart.iste.meitrex.common.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UmlEvaluationService {

    private final OllamaClient ollamaClient;

    private static final String TEMPLATE_ANALYSIS = "uml_analysis.md";
    private static final String TEMPLATE_GRADING = "uml_grading.md";


    @Transactional
    public void generateFeedback(
            final UmlStudentSolutionEntity solution,
            final String tutorModel,
            final String gradingRules,
            final int totalPoints
    ) {
        log.info("Starting automated feedback generation for solution ID: {}", solution.getId());
        final String studentModel = solution.getDiagram().getSemanticModel();
        UmlAnalysisResponse analysis = performAnalysis(studentModel, tutorModel);

        log.info("Analysis completed. Valid: {}, Summary: {}",
                analysis.isSemanticallyValid(), analysis.analysisSummary());

        UmlFeedbackResponse grading = performGrading(analysis, gradingRules, totalPoints);

        UmlFeedbackEntity feedbackEntity = UmlFeedbackEntity.builder()
            .solution(solution)
            .comment(grading.feedbackText())
            .points(grading.points())
            .build();

        solution.setFeedback(feedbackEntity);
    }

    private UmlAnalysisResponse performAnalysis(String studentModel, String tutorModel) {
        Map<String, String> args = Map.of(
            "studentModel", studentModel,
            "tutorModel", tutorModel
        );

        UmlAnalysisResponse fallback = new UmlAnalysisResponse(
            Collections.emptyList(), // correctElements
            Collections.emptyList(), // semanticErrors
            Collections.emptyList(), // missingElements
            false,
            "Analysis failed."
        );

        UmlAnalysisResponse response = ollamaClient.startQuery(
                UmlAnalysisResponse.class, TEMPLATE_ANALYSIS, args, fallback, null);

        log.info("Detailed Analysis Findings:\n - Correct: {}\n - Errors: {}\n - Missing: {}",
                response.correctElements(), response.semanticErrors(), response.missingElements());

        return response;
    }

    private UmlFeedbackResponse performGrading(UmlAnalysisResponse analysis, String rules, int maxPoints) {
        String effectiveRules = (rules != null && !rules.isBlank()) ? rules : "Standard UML grading.";

        Map<String, String> args = Map.of(
                "maxPoints", String.valueOf(maxPoints),
                "gradingRules", effectiveRules,
                "isValid", String.valueOf(analysis.isSemanticallyValid()),
                "correctElements", formatListForPrompt(analysis.correctElements()),
                "semanticErrors", formatListForPrompt(analysis.semanticErrors()),
                "missingElements", formatListForPrompt(analysis.missingElements())
        );

        UmlFeedbackResponse fallback = new UmlFeedbackResponse(
            "Grading unavailable. Please review manually.",
            0
        );

        return ollamaClient.startQuery(UmlFeedbackResponse.class, TEMPLATE_GRADING, args, fallback, null);
    }

    /**
     * Safely formats a list of strings into a single semicolon-separated string.
     * <p>
     * If the list is null or empty, it returns a default "None identified." string
     * to ensure the LLM receives explicit context rather than an empty field.
     * </p>
     *
     * @param list The list of strings to format.
     * @return A semicolon-separated string of the list items, or "None identified.".
     */
    private String formatListForPrompt(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "None identified.";
        }
        return String.join("; ", list);
    }
}
