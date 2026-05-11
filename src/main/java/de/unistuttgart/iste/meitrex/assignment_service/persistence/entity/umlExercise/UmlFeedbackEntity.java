package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.*;
import lombok.*;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;

import java.util.UUID;

@Entity(name = "UmlFeedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "solution_id")
    private UmlStudentSolutionEntity solution;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private int points;
}
