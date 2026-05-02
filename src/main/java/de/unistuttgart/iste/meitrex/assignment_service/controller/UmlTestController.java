package de.unistuttgart.iste.meitrex.assignment_service.controller;

import de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment.UmlEvaluationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/test-eval")
@RequiredArgsConstructor
@Slf4j
public class UmlTestController {

    private final UmlEvaluationService evaluationService;

    @PostMapping
    public TestEvalResponse runTest(@RequestBody TestEvalRequest request) {
        log.info("Running test: " + request.getAnalysisModel());
        return evaluationService.dryRunEvaluation(
                request.getStudentModel(),
                request.getTutorModel(),
                request.getGradingRules(),
                request.getMaxPoints(),
                request.getRequiredPercentage(),
                request.getAnalysisModel(),
                request.getGradingModel(),
                request.getAnalysisMode(),
                request.getTaskDescription()
        );
    }

    @Data
    public static class TestEvalRequest {
        private String studentModel;
        private String tutorModel;
        private String gradingRules;
        private int maxPoints;
        private double requiredPercentage;
        private String analysisModel;
        private String gradingModel;
        private String analysisMode;
        private String taskDescription;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TestEvalResponse {
        private int points;
        private String feedbackText;
        private double analysisDurationSeconds;
        private double gradingDurationSeconds;
        private double totalDurationSeconds;
    }
}