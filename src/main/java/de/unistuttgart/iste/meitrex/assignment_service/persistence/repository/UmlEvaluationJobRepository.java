package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlEvaluationJobEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlEvaluationJobStatus;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UmlEvaluationJobRepository extends MeitrexRepository<UmlEvaluationJobEntity, UUID> {

    /**
     * Finds the first job with the given status, ordered by creation date.
     */
    Optional<UmlEvaluationJobEntity> findFirstByStatusOrderByCreatedAt(UmlEvaluationJobStatus status);

    /**
     * Finds all jobs with a specific status.
     */
    List<UmlEvaluationJobEntity> findAllByStatus(UmlEvaluationJobStatus status);

    /**
     * Finds a job by solution ID.
     */
    Optional<UmlEvaluationJobEntity> findBySolutionId(UUID solutionId);
}
