package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity(name = "Grading")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingEntity implements IWithId<GradingEntity.PrimaryKey> {

    @EmbeddedId
    private PrimaryKey primaryKey;

    @Column(nullable = false)
    private OffsetDateTime date;

    @Column(nullable = false)
    private double achievedCredits;

    @Column(nullable = false)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parentGrading")
    private List<ExerciseGradingEntity> exerciseGradings;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private UUID assessmentId;
        private UUID userId;

    }

    @Override
    public PrimaryKey getId() {
        return primaryKey;
    }

}
