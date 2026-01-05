package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UmlStudentSolutionRepository extends MeitrexRepository<UmlStudentSolutionEntity, UUID> {

    /**
     * Finds all solutions for a specific student and exercise,
     * ordered by submission date (latest first).
     */
    @Query("SELECT s FROM UmlStudentSolution s " +
            "WHERE s.submission.studentId = :studentId " +
            "AND s.submission.exercise.assessmentId = :assessmentId " +
            "ORDER BY s.submittedAt DESC")
    List<UmlStudentSolutionEntity> findAllByStudentIdAndAssessmentId(
            @Param("studentId") UUID studentId,
            @Param("assessmentId") UUID assessmentId);

    /**
     * Fetches a solution along with its feedback to avoid N+1 queries
     * when displaying history in the frontend.
     */
    @Query("SELECT s FROM UmlStudentSolution s " +
            "LEFT JOIN FETCH s.feedback " +
            "WHERE s.id = :solutionId")
    UmlStudentSolutionEntity findByIdWithFeedback(@Param("solutionId") UUID solutionId);
}
