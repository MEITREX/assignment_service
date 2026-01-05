package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlExerciseEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UmlExerciseRepository extends MeitrexRepository<de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlExerciseEntity, UUID> {

    /**
     * Finds the UML exercise by its assessmentId and fetches the submissions
     * to avoid lazy loading issues in the gateway/controller.
     */
    @Query("SELECT e FROM UmlExercise e " +
            "LEFT JOIN FETCH e.studentSubmissions s " +
            "WHERE e.assessmentId = :assessmentId")
    Optional<UmlExerciseEntity> findByAssessmentIdWithSubmissions(@Param("assessmentId") UUID assessmentId);

    boolean existsByAssessmentId(UUID assessmentId);
}
