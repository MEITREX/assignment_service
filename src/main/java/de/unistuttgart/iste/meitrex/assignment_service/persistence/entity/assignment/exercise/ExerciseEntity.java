package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity(name = "Exercise")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseEntity implements IWithId<UUID> {
    @Id
    private UUID itemId;

    @Column(nullable = false)
    private double totalExerciseCredits;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parentExercise")
    private List<SubexerciseEntity> subexercises;

    @Column(nullable = true)
    private String number;

    @Column(nullable = true)
    private String tutorFeedback;

    @ManyToOne
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AssignmentEntity parentAssignment;

    @Override
    public UUID getId() {
        return itemId;
    }
}
