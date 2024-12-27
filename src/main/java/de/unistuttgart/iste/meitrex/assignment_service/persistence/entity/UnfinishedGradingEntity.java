package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.UUID;

@Entity(name = "UnfinishedGrading")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnfinishedGradingEntity implements IWithId<UnfinishedGradingEntity.PrimaryKey> {

    @EmbeddedId
    private PrimaryKey primaryKey;

    @Column(nullable = false)
    private String gradingJson;

    @Column(nullable = false)
    private int numberOfTries;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrimaryKey implements Serializable {
        private String externalStudentId;
        private UUID assignmentId;
    }

    @Override
    public PrimaryKey getId() {
        return primaryKey;
    }

    public void incrementNumberOfTries() {
        numberOfTries++;
    }

    public static UnfinishedGradingEntity fromJson(JSONObject gradingJson, UUID assignmentId) {
        String studentId = gradingJson.getString("studentId");
        return new UnfinishedGradingEntity(new PrimaryKey(studentId, assignmentId), gradingJson.toString(), 0);
    }

}
