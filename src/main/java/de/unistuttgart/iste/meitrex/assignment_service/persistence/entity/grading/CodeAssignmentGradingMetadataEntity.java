package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Stores metadata for code-based assignments gradings that are linked to external systems,
 * such as GitHub Classroom or other code exercise platforms.
 * <p>
 */
@Entity(name = "CodeAssignmentGradingMetadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAssignmentGradingMetadataEntity {
    @EmbeddedId
    private GradingEntity.PrimaryKey id;

    @OneToOne
    @MapsId
    @JoinColumns({
            @JoinColumn(name = "assessment_id", referencedColumnName = "assessment_id"),
            @JoinColumn(name = "student_id", referencedColumnName = "student_id")
    })
    private GradingEntity grading;

    @Column(nullable = true)
    private String repoLink;

    @Column(nullable = true)
    private String status;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String feedbackTableHtml;
}