package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.*;
import lombok.*;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "UmlStudentSolution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlStudentSolutionEntity implements IWithId<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private UmlStudentSubmissionEntity submission;

    @Column(nullable = true)
    private OffsetDateTime submittedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagram;

    @OneToOne(mappedBy = "solution", cascade = CascadeType.ALL)
    private UmlFeedbackEntity feedback;
}
