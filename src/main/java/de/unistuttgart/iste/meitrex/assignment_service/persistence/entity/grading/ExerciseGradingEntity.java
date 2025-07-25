package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Entity(name = "ExerciseGrading")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseGradingEntity {

    @EmbeddedId
    private PrimaryKey primaryKey;

    @Column(nullable = false)
    private double achievedCredits;

    @Column(nullable = false)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parentExerciseGrading")
    private List<SubexerciseGradingEntity> subexerciseGradings;

    @ManyToOne
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private GradingEntity parentGrading;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private UUID itemId;
        private UUID studentId;

    }

}