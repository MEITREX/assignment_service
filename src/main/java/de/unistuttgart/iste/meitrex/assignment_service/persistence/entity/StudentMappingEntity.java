package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity(name = "StudentMapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMappingEntity implements IWithId<String>{

    @Id
    private String externalStudentId;

    @Column(nullable = false)
    private UUID meitrexStudentId;

    @Override
    public String getId() {
        return externalStudentId;
    }

}
