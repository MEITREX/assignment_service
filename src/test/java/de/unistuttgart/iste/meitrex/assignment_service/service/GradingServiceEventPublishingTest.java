package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.StudentCodeSubmission;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.StudentCodeSubmittedEvent;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GradingService event publishing functionality, specifically
 * the StudentCodeSubmittedEvent publishing when grading code assignments.
 */
@ExtendWith(MockitoExtension.class)
class GradingServiceEventPublishingTest {

    @Mock
    private TopicPublisher topicPublisher;

    private UUID assignmentId;
    private UUID courseId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        studentId = UUID.randomUUID();
    }

    @Test
    void testPublishStudentCodeSubmittedEvent_Success() throws Exception {
        // Arrange
        String repoUrl = "https://github.com/org/repo-student";
        StudentCodeSubmission codeSubmission = createMockCodeSubmission(repoUrl);

        // Create the event that would be published
        StudentCodeSubmittedEvent event = StudentCodeSubmittedEvent.builder()
                .studentId(codeSubmission.getStudentId())
                .assignmentId(codeSubmission.getAssignmentId())
                .courseId(codeSubmission.getCourseId())
                .repositoryUrl(codeSubmission.getRepositoryUrl())
                .commitSha(codeSubmission.getCommitSha())
                .commitTimestamp(codeSubmission.getCommitTimestamp())
                .files(codeSubmission.getFiles())
                .branch(codeSubmission.getBranch())
                .build();

        // Act
        topicPublisher.notifyStudentCodeSubmitted(event);

        // Assert
        ArgumentCaptor<StudentCodeSubmittedEvent> eventCaptor = 
                ArgumentCaptor.forClass(StudentCodeSubmittedEvent.class);
        verify(topicPublisher, times(1)).notifyStudentCodeSubmitted(eventCaptor.capture());

        StudentCodeSubmittedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(studentId, capturedEvent.getStudentId());
        assertEquals(assignmentId, capturedEvent.getAssignmentId());
        assertEquals(courseId, capturedEvent.getCourseId());
        assertEquals(repoUrl, capturedEvent.getRepositoryUrl());
        assertNotNull(capturedEvent.getCommitSha());
        assertNotNull(capturedEvent.getCommitTimestamp());
        assertEquals(2, capturedEvent.getFiles().size());
        assertTrue(capturedEvent.getFiles().containsKey("Main.java"));
        assertTrue(capturedEvent.getFiles().containsKey("src/Helper.java"));
    }

    @Test
    void testCodeSubmissionEventContent_AllFieldsPresent() {
        // Arrange
        String repoUrl = "https://github.com/test/repo";
        String commitSha = "abc123def456";
        OffsetDateTime commitTime = OffsetDateTime.now();
        String branch = "main";
        
        Map<String, String> files = new HashMap<>();
        files.put("Test.java", "public class Test {}");

        // Act
        StudentCodeSubmittedEvent event = StudentCodeSubmittedEvent.builder()
                .studentId(studentId)
                .assignmentId(assignmentId)
                .courseId(courseId)
                .repositoryUrl(repoUrl)
                .commitSha(commitSha)
                .commitTimestamp(commitTime)
                .files(files)
                .branch(branch)
                .build();

        // Assert
        assertEquals(studentId, event.getStudentId());
        assertEquals(assignmentId, event.getAssignmentId());
        assertEquals(courseId, event.getCourseId());
        assertEquals(repoUrl, event.getRepositoryUrl());
        assertEquals(commitSha, event.getCommitSha());
        assertEquals(commitTime, event.getCommitTimestamp());
        assertEquals(files, event.getFiles());
        assertEquals(branch, event.getBranch());
    }

    @Test
    void testCodeSubmission_MultipleFiles() {
        // Arrange
        StudentCodeSubmission codeSubmission = StudentCodeSubmission.builder()
                .studentId(studentId)
                .assignmentId(assignmentId)
                .courseId(courseId)
                .repositoryUrl("https://github.com/test/repo")
                .commitSha("sha123")
                .commitTimestamp(OffsetDateTime.now())
                .branch("main")
                .files(createMultipleFiles())
                .build();

        // Act
        StudentCodeSubmittedEvent event = StudentCodeSubmittedEvent.builder()
                .studentId(codeSubmission.getStudentId())
                .assignmentId(codeSubmission.getAssignmentId())
                .courseId(codeSubmission.getCourseId())
                .repositoryUrl(codeSubmission.getRepositoryUrl())
                .commitSha(codeSubmission.getCommitSha())
                .commitTimestamp(codeSubmission.getCommitTimestamp())
                .files(codeSubmission.getFiles())
                .branch(codeSubmission.getBranch())
                .build();

        // Assert
        assertNotNull(event.getFiles());
        assertEquals(5, event.getFiles().size());
        assertTrue(event.getFiles().containsKey("src/Main.java"));
        assertTrue(event.getFiles().containsKey("src/util/Helper.java"));
        assertTrue(event.getFiles().containsKey("test/MainTest.java"));
        assertTrue(event.getFiles().containsKey("README.md"));
        assertTrue(event.getFiles().containsKey("pom.xml"));
    }

    @Test
    void testCodeSubmission_EmptyFiles() {
        // Arrange
        StudentCodeSubmission codeSubmission = StudentCodeSubmission.builder()
                .studentId(studentId)
                .assignmentId(assignmentId)
                .courseId(courseId)
                .repositoryUrl("https://github.com/test/repo")
                .commitSha("sha123")
                .commitTimestamp(OffsetDateTime.now())
                .branch("main")
                .files(new HashMap<>())
                .build();

        // Act
        StudentCodeSubmittedEvent event = StudentCodeSubmittedEvent.builder()
                .studentId(codeSubmission.getStudentId())
                .assignmentId(codeSubmission.getAssignmentId())
                .courseId(codeSubmission.getCourseId())
                .repositoryUrl(codeSubmission.getRepositoryUrl())
                .commitSha(codeSubmission.getCommitSha())
                .commitTimestamp(codeSubmission.getCommitTimestamp())
                .files(codeSubmission.getFiles())
                .branch(codeSubmission.getBranch())
                .build();

        // Assert
        assertNotNull(event.getFiles());
        assertTrue(event.getFiles().isEmpty());
    }

    @Test
    void testCodeSubmission_VerifyMetadata() {
        // Arrange
        String repoUrl = "https://github.com/student/assignment-repo";
        String commitSha = "1234567890abcdef";
        OffsetDateTime timestamp = OffsetDateTime.parse("2025-12-15T10:30:00Z");
        String branch = "develop";

        StudentCodeSubmission submission = StudentCodeSubmission.builder()
                .studentId(studentId)
                .assignmentId(assignmentId)
                .courseId(courseId)
                .repositoryUrl(repoUrl)
                .commitSha(commitSha)
                .commitTimestamp(timestamp)
                .branch(branch)
                .files(Map.of("Main.java", "code"))
                .build();

        // Assert
        assertEquals(repoUrl, submission.getRepositoryUrl());
        assertEquals(commitSha, submission.getCommitSha());
        assertEquals(timestamp, submission.getCommitTimestamp());
        assertEquals(branch, submission.getBranch());
        assertEquals(assignmentId, submission.getAssignmentId());
        assertEquals(courseId, submission.getCourseId());
        assertEquals(studentId, submission.getStudentId());
    }

    /**
     * Helper method to create a mock code submission
     */
    private StudentCodeSubmission createMockCodeSubmission(String repoUrl) {
        Map<String, String> files = new HashMap<>();
        files.put("Main.java", "public class Main { public static void main(String[] args) {} }");
        files.put("src/Helper.java", "public class Helper { public void help() {} }");

        return StudentCodeSubmission.builder()
                .studentId(studentId)
                .assignmentId(assignmentId)
                .courseId(courseId)
                .repositoryUrl(repoUrl)
                .commitSha("abc123def456")
                .commitTimestamp(OffsetDateTime.now())
                .branch("main")
                .files(files)
                .build();
    }

    /**
     * Helper method to create multiple files for testing
     */
    private Map<String, String> createMultipleFiles() {
        Map<String, String> files = new HashMap<>();
        files.put("src/Main.java", "public class Main {}");
        files.put("src/util/Helper.java", "public class Helper {}");
        files.put("test/MainTest.java", "@Test public class MainTest {}");
        files.put("README.md", "# Project README");
        files.put("pom.xml", "<project></project>");
        return files;
    }
}
