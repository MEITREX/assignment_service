package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends MeitrexRepository<AssignmentEntity, UUID> {
    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.codeAssignmentMetadata WHERE a.assessmentId = :id")
    Optional<AssignmentEntity> findByIdWithCodeMetadata(@Param("id") UUID id);

    boolean existsByExternalId(String externalId);
}
