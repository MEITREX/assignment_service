package de.unistuttgart.iste.meitrex.assignment_service.service.uml_assignment;

import java.util.List;

/**
 * Result of the first LLM step: purely analytical comparison.
 */
public record UmlAnalysisResponse(
        List<String> semanticErrors,
        List<String> missingElements,
        List<String> correctElements,
        boolean isSemanticallyValid,
        String analysisSummary
) {
    public UmlAnalysisResponse {
        correctElements = correctElements == null ? List.of() : correctElements;
        semanticErrors = semanticErrors == null ? List.of() : semanticErrors;
        missingElements = missingElements == null ? List.of() : missingElements;
    }
}
