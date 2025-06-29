package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ManualMappingInstanceEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ManualMappingInstanceRepository extends MeitrexRepository<ManualMappingInstanceEntity, String> {
}
