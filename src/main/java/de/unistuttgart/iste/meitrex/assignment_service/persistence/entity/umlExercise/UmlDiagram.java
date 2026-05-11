package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlDiagram {

    @Column(name = "diagram", columnDefinition = "TEXT")
    private String diagramCode;

    @Column(columnDefinition = "TEXT")
    private String semanticModel;

    /**
     * Helper to check if the diagram is actually "empty"
     */
    public boolean isEmpty() {
        return (diagramCode == null || diagramCode.isBlank()) && (semanticModel == null || semanticModel.isBlank());
    }
}
