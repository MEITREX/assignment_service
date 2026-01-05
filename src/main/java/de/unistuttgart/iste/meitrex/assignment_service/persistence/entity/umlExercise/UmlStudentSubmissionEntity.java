package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise;

import jakarta.persistence.*;
import lombok.*;
import de.unistuttgart.iste.meitrex.common.persistence.IWithId;

import java.util.List;
import java.util.UUID;

@Entity(name = "UmlStudentSubmission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlStudentSubmissionEntity implements IWithId<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private UmlExerciseEntity exercise;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("submittedAt DESC")
    private List<UmlStudentSolutionEntity> solutions;
}
