package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AssignmentRepository extends MeitrexRepository<AssignmentEntity, UUID> {


}
