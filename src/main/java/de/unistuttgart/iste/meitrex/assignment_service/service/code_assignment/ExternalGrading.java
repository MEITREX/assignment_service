package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

public record ExternalGrading(String externalUsername, String status, String date, String tableHtml, double achievedPoints, double totalPoints) {
}