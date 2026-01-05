package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.*;
import lombok.*;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;

import java.util.List;
import java.util.UUID;

@Entity(name = "UmlExercise")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlExerciseEntity implements IWithId<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID assessmentId;

    @Column(nullable = false)
    private UUID courseId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean showSolution;

    @Column(columnDefinition = "TEXT")
    private String tutorSolution;

    @Column(nullable = false)
    private int totalPoints;

    @Column(nullable = false)
    private double requiredPercentage;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UmlStudentSubmissionEntity> studentSubmissions;
}
