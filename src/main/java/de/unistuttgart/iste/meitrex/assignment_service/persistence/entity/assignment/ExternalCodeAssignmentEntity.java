package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity(name = "ExternalCodeAssignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCodeAssignmentEntity implements IWithId<ExternalCodeAssignmentEntity.PrimaryKey> {

    @EmbeddedId
    private ExternalCodeAssignmentEntity.PrimaryKey primaryKey;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String assignmentLink;

    @Column(nullable = false)
    private String invitationLink;

    private OffsetDateTime dueDate;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    private String readmeHtml;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private String courseTitle;
        private String assignmentName;
    }

    @Override
    public ExternalCodeAssignmentEntity.PrimaryKey getId() {
        return primaryKey;
    }
}
