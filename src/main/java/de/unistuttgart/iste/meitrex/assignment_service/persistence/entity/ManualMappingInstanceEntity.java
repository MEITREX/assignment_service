package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

@Entity(name = "ManualMappingInstance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualMappingInstanceEntity implements IWithId<String> {

    @Id
    private String externalStudentId;

    @Column(nullable = false)
    private String externalStudentInfo;

    @Override
    public String getId() {
        return externalStudentId;
    }

    public static ManualMappingInstanceEntity fromJson(JSONObject externalStudentInfo) {
        return new ManualMappingInstanceEntity(externalStudentInfo.getString("id"), externalStudentInfo.toString());
    }

}
