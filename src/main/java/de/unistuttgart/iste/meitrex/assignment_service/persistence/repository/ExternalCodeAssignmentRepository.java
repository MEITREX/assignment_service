package de.unistuttgart.iste.meitrex.assignment_service.persistence.repository;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.common.persistence.MeitrexRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExternalCodeAssignmentRepository extends MeitrexRepository<ExternalCodeAssignmentEntity, ExternalCodeAssignmentEntity.PrimaryKey> {
    @Query("SELECT e.primaryKey.assignmentName FROM ExternalCodeAssignment e WHERE e.primaryKey.courseTitle = :courseTitle")
    List<String> findAssignmentNamesByCourseTitle(@Param("courseTitle") String courseTitle);
}