package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSubmissionEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UmlStudentSubmissionRepository extends MeitrexRepository<UmlStudentSubmissionEntity, UUID> {

    @Query("SELECT s FROM UmlStudentSubmission s " +
            "LEFT JOIN FETCH s.solutions " +
            "WHERE s.studentId = :studentId AND s.exercise.assessmentId = :assessmentId")
    Optional<UmlStudentSubmissionEntity> findByStudentAndAssessmentWithSolutions(
            @Param("studentId") UUID studentId,
            @Param("assessmentId") UUID assessmentId);
}