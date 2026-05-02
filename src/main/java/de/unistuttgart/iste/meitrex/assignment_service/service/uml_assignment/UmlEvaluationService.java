package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.controller.UmlTestController;
import de.unistuttgart.iste.meitrex.assignment_service.exception.AiEvaluationException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlEvaluationJobEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlFeedbackEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ServerSource;
import de.unistuttgart.iste.meitrex.common.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UmlEvaluationService {

    private final OllamaClient ollamaClient;
    private final TopicPublisher topicPublisher;

    private static final String TEMPLATE_ANALYSIS = "uml_analysis.md";
    private static final String TEMPLATE_GRADING = "uml_grading.md";


    @Transactional
    public void generateFeedback(
            final UmlStudentSolutionEntity solution,
            final String tutorModel,
            final String gradingRules,
            final int totalPoints,
            final double requiredPercentage,
            final boolean showSolution
    ) {
        log.info("Starting automated feedback generation for solution ID: {}", solution.getId());
        final String studentModel = solution.getDiagram().getSemanticModel();
        UmlAnalysisResponse analysis = performAnalysis(studentModel, tutorModel);

        log.info("Analysis completed. Valid: {}, Summary: {}",
                analysis.isSemanticallyValid(), analysis.analysisSummary());

        UmlFeedbackResponse grading = performGrading(
                analysis, gradingRules, totalPoints, requiredPercentage, showSolution);

        UmlFeedbackEntity feedbackEntity = UmlFeedbackEntity.builder()
            .solution(solution)
            .comment(grading.feedbackText())
            .points(grading.points())
            .build();

        solution.setFeedback(feedbackEntity);

        UUID studentUserId = solution.getSubmission().getStudentId();
        UUID courseId = solution.getSubmission().getExercise().getCourseId();
        UUID assessmentId = solution.getSubmission().getExercise().getAssessmentId();
        String link = "/courses/" + courseId + "/uml/" + assessmentId;
        topicPublisher.notificationEvent(
            courseId,
            List.of(studentUserId),
            ServerSource.COURSE,
            link,
            "Your UML submission was evaluated",
            "Feedback and points are now available."
        );
    }

    /**
     * Alternative version of generateFeedback for use with the evaluation queue.
     * Called from UmlEvaluationQueueService when processing jobs.
     * This method fetches the exercise details from the solution and performs the evaluation.
     */
    @Transactional
    public void generateFeedbackForJob(
            final UmlStudentSolutionEntity solution,
            final UmlEvaluationJobEntity job
    ) {
        // Get exercise from the submission
        if (solution.getSubmission() == null || solution.getSubmission().getExercise() == null) {
            throw new IllegalStateException("Solution is missing exercise information");
        }

        final var exercise = solution.getSubmission().getExercise();

        // Call the standard generateFeedback method with exercise details
        generateFeedback(
            solution,
            exercise.getTutorSolution().getSemanticModel(),
            exercise.getGradingRules(),
            exercise.getTotalPoints(),
            exercise.getRequiredPercentage(),
            exercise.isShowSolution()
        );
    }

    private UmlAnalysisResponse performAnalysis(String studentModel, String tutorModel) throws AiEvaluationException {
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

        if ("Analysis failed.".equals(response.analysisSummary())) {
            log.error("Ollama client returned the fallback response. Aborting evaluation.");
            throw new AiEvaluationException("The AI model failed to analyze the UML diagram.");
        }

        log.info("Detailed Analysis Findings:\n - Correct: {}\n - Errors: {}\n - Missing: {}",
                response.correctElements(), response.semanticErrors(), response.missingElements());

        return response;
    }

    private UmlFeedbackResponse performGrading(
            final UmlAnalysisResponse analysis,
            final String rules,
            final int maxPoints,
            final double requiredPercentage,
            final boolean showSolution
    ) throws AiEvaluationException {
        String effectiveRules = (rules != null && !rules.isBlank()) ? rules : "Standard UML grading.";

        Map<String, String> args = Map.of(
                "maxPoints", String.valueOf(maxPoints),
                "passingThreshold", String.valueOf(requiredPercentage * maxPoints),
                "showSolution", String.valueOf(showSolution),
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

        UmlFeedbackResponse response = ollamaClient.startQuery(
                UmlFeedbackResponse.class, TEMPLATE_GRADING, args, fallback, null);

        // Intercept the fallback to prevent saving a 0-point grade
        if ("Grading unavailable. Please review manually.".equals(response.feedbackText())) {
            log.error("Ollama client returned the fallback response for grading. Aborting evaluation.");
            throw new AiEvaluationException("The AI model successfully analyzed the diagram but failed to generate a final grade. Please try again.");
        }

        return response;
    }

    /**
     * Executes the evaluation pipeline without saving to the database.
     * Returns granular timings for benchmarking LLM configurations.
     */
    public UmlTestController.TestEvalResponse dryRunEvaluation(
            final String studentModel,
            final String tutorModel,
            final String gradingRules,
            final int maxPoints,
            final double requiredPercentage,
            final String analysisModelOverride,
            final String gradingModelOverride,
            final String promptMode,
            final String taskDescription
    ) {
        long startTotal = System.currentTimeMillis();

        // 1. Run Analysis with Timer
        long startAnalysis = System.currentTimeMillis();
        Map<String, String> analysisArgs = new HashMap<>();
        analysisArgs.put("studentModel", studentModel);

        UmlAnalysisResponse fallbackAnalysis = new UmlAnalysisResponse(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, "Analysis failed."
        );

        String prompt;
        if ("TASK_BASED".equals(promptMode)) {
            prompt = "analysis_task.md";
            analysisArgs.put("taskDescription", taskDescription);
            analysisArgs.put("tutorModel", tutorModel);
        } else if ("PURE_TASK".equals(promptMode)) {
            prompt = "uml_analysis_pure_task.md";
            analysisArgs.put("taskDescription", taskDescription);
        } else {
            prompt = TEMPLATE_ANALYSIS;
            analysisArgs.put("tutorModel", tutorModel);
        }

        UmlAnalysisResponse analysis = ollamaClient.startQuery(
                UmlAnalysisResponse.class, prompt, analysisArgs, fallbackAnalysis, analysisModelOverride);

        long endAnalysis = System.currentTimeMillis();
        double analysisDuration = (endAnalysis - startAnalysis) / 1000.0;

        if ("Analysis failed.".equals(analysis.analysisSummary())) {
            throw new RuntimeException("Analysis LLM failed during dry run.");
        }

        // 2. Run Grading with Timer
        long startGrading = System.currentTimeMillis();
        String effectiveRules = (gradingRules != null && !gradingRules.isBlank()) ? gradingRules : "Standard UML grading.";
        Map<String, String> gradingArgs = Map.of(
                "maxPoints", String.valueOf(maxPoints),
                "passingThreshold", String.valueOf(requiredPercentage * maxPoints),
                "showSolution", "true",
                "gradingRules", effectiveRules,
                "isValid", String.valueOf(analysis.isSemanticallyValid()),
                "correctElements", formatListForPrompt(analysis.correctElements()),
                "semanticErrors", formatListForPrompt(analysis.semanticErrors()),
                "missingElements", formatListForPrompt(analysis.missingElements())
        );

        UmlFeedbackResponse fallbackGrading = new UmlFeedbackResponse("Grading unavailable.", 0);

        UmlFeedbackResponse grading = ollamaClient.startQuery(
                UmlFeedbackResponse.class, TEMPLATE_GRADING, gradingArgs, fallbackGrading, gradingModelOverride);

        long endGrading = System.currentTimeMillis();
        double gradingDuration = (endGrading - startGrading) / 1000.0;

        if ("Grading unavailable.".equals(grading.feedbackText())) {
            throw new RuntimeException("Grading LLM failed during dry run.");
        }

        long endTotal = System.currentTimeMillis();
        double totalDuration = (endTotal - startTotal) / 1000.0;

        // Return the combined payload
        return new UmlTestController.TestEvalResponse(
                grading.points(),
                grading.feedbackText(),
                analysisDuration,
                gradingDuration,
                totalDuration
        );
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
