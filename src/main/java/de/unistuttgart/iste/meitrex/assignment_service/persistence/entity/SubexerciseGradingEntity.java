package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity(name = "SubexerciseGrading")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubexerciseGradingEntity {

    @EmbeddedId
    private PrimaryKey primaryKey;

    @Column(nullable = false)
    private double achievedCredits;

    @ManyToOne
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ExerciseGradingEntity parentExerciseGrading;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private UUID itemId;
        private UUID studentId;

    }
}
