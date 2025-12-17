package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.GithubClassroom;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.StudentCodeSubmission;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.AccessToken;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FetchStudentCodeTest {

    private MockWebServer mockWebServer;
    private GithubClassroom githubClassroom;
    private UserServiceClient userServiceClient;
    private AssignmentRepository assignmentRepository;
    private ExternalCodeAssignmentRepository externalCodeAssignmentRepository;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        userServiceClient = mock(UserServiceClient.class);
        assignmentRepository = mock(AssignmentRepository.class);
        externalCodeAssignmentRepository = mock(ExternalCodeAssignmentRepository.class);

        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        githubClassroom = new GithubClassroom(
                userServiceClient,
                assignmentRepository,
                externalCodeAssignmentRepository,
                baseUrl
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testFetchStudentCode_success() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any()))
                .thenReturn(AccessToken.builder()
                        .setAccessToken(token)
                        .setExternalUserId("alice")
                        .build());

        // Mock repository info response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                {
                  "default_branch": "main",
                  "name": "assignment-1-alice"
                }
                """));

        // Mock commit info response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                {
                  "sha": "abc123def456",
                  "commit": {
                    "committer": {
                      "date": "2025-12-15T10:30:00Z"
                    }
                  }
                }
                """));

        // Mock repository contents (root directory)
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                [
                  {
                    "name": "Main.java",
                    "path": "Main.java",
                    "type": "file",
                    "download_url": "%s/file/Main.java"
                  },
                  {
                    "name": "src",
                    "path": "src",
                    "type": "dir"
                  }
                ]
                """.formatted(mockWebServer.url(""))));

        // Mock file content for Main.java
        mockWebServer.enqueue(new MockResponse()
                .setBody("public class Main { public static void main(String[] args) { System.out.println(\"Hello\"); } }"));

        // Mock src directory contents
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                [
                  {
                    "name": "Helper.java",
                    "path": "src/Helper.java",
                    "type": "file",
                    "download_url": "%s/file/src/Helper.java"
                  }
                ]
                """.formatted(mockWebServer.url(""))));

        // Mock file content for src/Helper.java
        mockWebServer.enqueue(new MockResponse()
                .setBody("public class Helper { public void help() { System.out.println(\"Helping\"); } }"));

        String repoUrl = "https://github.com/org-name/assignment-1-alice";
        StudentCodeSubmission result = githubClassroom.fetchStudentCode(repoUrl, user);

        assertNotNull(result);
        assertEquals(repoUrl, result.getRepositoryUrl());
        assertEquals("abc123def456", result.getCommitSha());
        assertEquals("main", result.getBranch());
        assertNotNull(result.getCommitTimestamp());
        
        assertNotNull(result.getFiles());
        assertEquals(2, result.getFiles().size());
        assertTrue(result.getFiles().containsKey("Main.java"));
        assertTrue(result.getFiles().containsKey("src/Helper.java"));
        assertTrue(result.getFiles().get("Main.java").contains("public class Main"));
        assertTrue(result.getFiles().get("src/Helper.java").contains("public class Helper"));
    }

    @Test
    void testFetchStudentCode_invalidRepoUrl() throws Exception {
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any()))
                .thenReturn(AccessToken.builder()
                        .setAccessToken("token")
                        .setExternalUserId("alice")
                        .build());

        String invalidUrl = "https://github.com/invalid";

        assertThrows(ExternalPlatformConnectionException.class, () -> {
            githubClassroom.fetchStudentCode(invalidUrl, user);
        });
    }
}
