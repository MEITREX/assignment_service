package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalServiceProviderDto;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;

import java.util.List;

/**
 * Defines the contract for integrating with external code assessment platforms (e.g., GitHub Classroom).
 * <p>
 * Implementations of this interface are responsible for retrieving external course and assignment data,
 * synchronizing assignments and grades, and providing platform-specific metadata.
 * </p>
 */
public interface CodeAssessmentProvider {

    /**
     * Retrieves metadata for an external course by its title.
     *
     * @param courseTitle the title of the course (must match the external platform's naming)
     * @param currentUser the user performing the lookup; must be an ADMIN in the external course
     * @return an {@link ExternalCourse} containing title and access URL
     * @throws ExternalPlatformConnectionException if the external platform is unreachable or returns an error
     * @throws UserServiceConnectionException      if user-related data cannot be resolved
     */
    ExternalCourse getExternalCourse(String courseTitle, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;

    /**
     * Synchronizes all assignments for the given external course and stores them locally.
     * <p>
     * Called before assignment creation to preload metadata (e.g., deadlines, links, README).
     * </p>
     *
     * @param courseTitle the title of the course on the external platform
     * @param currentUser the user performing the lookup; must be an ADMIN in the external course
     * @throws ExternalPlatformConnectionException if the external platform is unreachable or returns an error
     * @throws UserServiceConnectionException      if user-related data cannot be resolved
     */
    void syncAssignmentsForCourse(String courseTitle, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;

    /**
     * Synchronizes grades for all students for a given external assignment.
     *
     * @param externalAssignmentId the identifier of the assignment on the external platform
     * @param currentUser the user performing the lookup; must be an ADMIN in the external course
     * @return a list of {@link ExternalGrading} entries for each student
     * @throws ExternalPlatformConnectionException if the external platform is unreachable or returns an error
     * @throws UserServiceConnectionException      if user-related data cannot be resolved
     */
    List<ExternalGrading> syncGrades(String externalAssignmentId, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;

    /**
     * Locates the student's repository for the given assignment.
     *
     * @param assignmentName the name of the assignment
     * @param organizationName the name of the organization (e.g., GitHub organization) used to find the repository
     * @param currentUser the user performing the lookup; must be a STUDENT in the external course
     * @throws ExternalPlatformConnectionException if the external platform is unreachable or returns an error
     * @throws UserServiceConnectionException      if user-related data cannot be resolved
     */
    String findRepository(String assignmentName, String organizationName, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;

    /**
     * Retrieves the latest grading information for a single student's submission based on the given repository link.
     *
     * @param repoLink    the link to the student's GitHub repository (or other external platform)
     * @param currentUser the user performing the lookup; must be a STUDENT in the external course
     * @return an {@link ExternalGrading} result for the student
     * @throws ExternalPlatformConnectionException if the external platform is unreachable or returns an error
     * @throws UserServiceConnectionException      if user-related data cannot be resolved
     */
    ExternalGrading syncGradeForStudent(String repoLink, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException;

    /**
     * Returns the name and metadata of the external service provider (e.g., GitHub).
     *
     * @return a DTO representing the provider's identity
     */
    ExternalServiceProviderDto getName();
}


