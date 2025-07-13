package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading;

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
    @AttributeOverrides({
            @AttributeOverride(name = "assessmentId", column = @Column(name = "assessment_id")),
            @AttributeOverride(name = "studentId", column = @Column(name = "student_id"))
    })
    private PrimaryKey primaryKey;

    @Column(nullable = true)
    private OffsetDateTime date;

    // is nullable, since for code assignments only after pushing code achieved credits can be fetched
    @Column(nullable = true)
    private Double achievedCredits;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parentGrading")
    private List<ExerciseGradingEntity> exerciseGradings;

    /**
     * Stores additional metadata for code-based assignments gradings.
     */
    @OneToOne(mappedBy = "grading", cascade = CascadeType.ALL, orphanRemoval = true)
    private CodeAssignmentGradingMetadataEntity codeAssignmentGradingMetadata;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private UUID assessmentId;
        private UUID studentId;

    }

    @Override
    public PrimaryKey getId() {
        return primaryKey;
    }

}
