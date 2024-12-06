package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.StudentMappingEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentMappingRepository extends MeitrexRepository<StudentMappingEntity, StudentMappingEntity.StudentMappingKey> {

}
