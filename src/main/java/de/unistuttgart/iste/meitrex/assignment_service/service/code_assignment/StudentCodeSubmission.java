package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a student's code submission from their GitHub repository.
 * Contains all source files and metadata about the submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCodeSubmission {
    
    /**
     * The ID of the student who made the submission
     */
    private UUID studentId;
    
    /**
     * The ID of the assignment this submission is for
     */
    private UUID assignmentId;
    
    /**
     * The ID of the course this assignment belongs to
     */
    private UUID courseId;
    
    /**
     * The GitHub repository URL
     */
    private String repositoryUrl;
    
    /**
     * The commit SHA of the code being submitted
     */
    private String commitSha;
    
    /**
     * The timestamp of the commit
     */
    private OffsetDateTime commitTimestamp;
    
    /**
     * Map of file paths to their content
     * Key: file path
     * Value: file content
     */
    private Map<String, String> files;
    
    /**
     * The name of the branch
     */
    private String branch;
}
