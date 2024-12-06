package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Entity(name = "StudentMapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMappingEntity implements IWithId<StudentMappingEntity.StudentMappingKey>{

    // extra short constructor
    public StudentMappingEntity(final UUID meitrexStudentId, final String externalStudentId) {
        studentMappingKey = new StudentMappingKey(meitrexStudentId, externalStudentId);
    }

    @EmbeddedId
    private StudentMappingKey studentMappingKey;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentMappingKey implements Serializable {
        private UUID meitrexStudentId;
        private String externalStudentId;
    }

    @Override
    public StudentMappingKey getId() {
        return studentMappingKey;
    }

}
