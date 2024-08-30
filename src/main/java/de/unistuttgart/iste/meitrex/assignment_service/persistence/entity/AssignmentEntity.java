package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity(name = "Assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentEntity implements IWithId<UUID> {

    @Id
    private UUID assessmentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parentAssignment")
    private List<ExerciseEntity> exercises;

    @Column(nullable = true)
    private OffsetDateTime date;

    @Column(nullable = false)
    private double totalCredits;

    @Column(nullable = false)
    private AssignmentType assignmentType;

    @Column(nullable = true)
    private String description;

    @Column(nullable = true)
    private Double requiredPercentage;

    @Override
    public UUID getId() {
        return assessmentId;
    }
}
