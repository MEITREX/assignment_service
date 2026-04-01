package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of the first LLM step: purely analytical comparison.
 */
public record UmlAnalysisResponse(
        @JsonProperty("semanticErrors") List<String> semanticErrors,
        @JsonProperty("missingElements") List<String> missingElements,
        @JsonProperty("correctElements") List<String> correctElements,
        @JsonProperty("isSemanticallyValid") boolean isSemanticallyValid,
        @JsonProperty("analysisSummary") String analysisSummary
) {
    public UmlAnalysisResponse {
        correctElements = correctElements == null ? List.of() : correctElements;
        semanticErrors = semanticErrors == null ? List.of() : semanticErrors;
        missingElements = missingElements == null ? List.of() : missingElements;
    }
}
