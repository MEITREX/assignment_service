package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import java.time.OffsetDateTime;

public record ExternalGrading(String externalUsername, String status, OffsetDateTime date, String tableHtml, double achievedPoints, double totalPoints) {
}