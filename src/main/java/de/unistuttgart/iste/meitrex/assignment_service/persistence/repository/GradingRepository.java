package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradingRepository extends MeitrexRepository<GradingEntity, GradingEntity.PrimaryKey> {
    List<GradingEntity> findAllByPrimaryKey_AssessmentId(UUID assessmentId);
    List<GradingEntity> findAllByPrimaryKey_AssessmentIdIn(List<UUID> ids);

}