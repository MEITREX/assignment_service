package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.*;
import lombok.*;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "UmlEvaluationJob")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlEvaluationJobEntity implements IWithId<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solution_id", nullable = false)
    private UmlStudentSolutionEntity solution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UmlEvaluationJobStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime completedAt;

    @Version
    private Long version;
}
