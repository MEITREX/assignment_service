package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.UnfinishedGradingEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface UnfinishedGradingRepository extends MeitrexRepository<UnfinishedGradingEntity, UnfinishedGradingEntity.PrimaryKey> {
}
