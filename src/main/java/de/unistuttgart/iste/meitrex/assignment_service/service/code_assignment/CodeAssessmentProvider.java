package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;

import java.util.List;

public interface CodeAssessmentProvider {
    void syncAssignmentsForCourse(String courseTitle, LoggedInUser currentUser) throws ExternalPlatformConnectionException, UserServiceConnectionException;
    List<ExternalGrading> syncGrades(String externalAssignmentId, LoggedInUser currentUser) throws ExternalPlatformConnectionException, UserServiceConnectionException;
    String findRepository(String assignmentName, LoggedInUser currentUser) throws ExternalPlatformConnectionException, UserServiceConnectionException;
    ExternalGrading syncGradeForStudent(String repoLink, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;
}

