package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity(name = "Subexercise")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubexerciseEntity implements IWithId<UUID> {
    @Id
    private UUID itemId;

    @Column(nullable = false)
    private double totalSubexerciseCredits;

    @Column(nullable = true)
    private String number;

    @Column(nullable = true)
    private String tutorFeedback;

    @ManyToOne
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ExerciseEntity parentExercise;

    @Override
    public UUID getId() {
        return itemId;
    }
}