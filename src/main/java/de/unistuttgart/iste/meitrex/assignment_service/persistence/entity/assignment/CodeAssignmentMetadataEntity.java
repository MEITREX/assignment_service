package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Stores metadata for code-based assignments that are linked to external systems,
 * such as GitHub Classroom or other code exercise platforms.
 * <p>
 */
@Entity(name = "CodeAssignmentMetadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAssignmentMetadataEntity {
    @Id
    private UUID id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "assignment_id")
    private AssignmentEntity assignment;

    /**
     * A link to the external course.
     */
    @Column(nullable = false)
    private String assignmentLink;

    /**
     * The invitation link for students to join the external assignment.
     */
    @Column(nullable = false)
    private String invitationLink;

    /**
     * The README file content for the external assignment, converted to HTML.
     * Optional and may be null if not provided by the external service.
     */
    @Column(nullable = true, columnDefinition = "TEXT")
    private String readmeHtml;
}
