package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.ExternalGrading;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.GithubClassroom;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import de.unistuttgart.iste.meitrex.generated.dto.AccessToken;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;


import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GithubClassroomTest {

    private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository = mock(ExternalCodeAssignmentRepository.class);

    private MockWebServer mockWebServer;
    private GithubClassroom githubClassroom;

    @BeforeEach
    void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        githubClassroom = new GithubClassroom(
                userServiceClient,
                assignmentRepository,
                externalCodeAssignmentRepository,
                mockWebServer.url("/").toString()
        );
    }

    @AfterEach
    void teardown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGetExternalCourse() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any())).thenReturn(
                AccessToken.builder().setAccessToken(token).build()
        );

        String responseBody = """
[
  { 
      "id": 1234,
        "name": "My Course",
    "url": "https://classroom.github.com/classrooms/1234"
  }
]
""";

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .setHeader("Content-Type", "application/json"));

        String detailResponse = """
    {
      "id": 1234,
      "name": "My Course",
      "url": "https://classroom.github.com/classrooms/1234",
      "organization": { "login": "testOrg" }
    }
    """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(detailResponse)
                .setHeader("Content-Type", "application/json"));

        ExternalCourse result = githubClassroom.getExternalCourse("My Course", user);

        assertEquals("My Course", result.getCourseTitle());
        assertEquals("https://classroom.github.com/classrooms/1234", result.getUrl());
        assertEquals("testOrg", result.getOrganizationName());
    }

    @Test
    void testSyncAssignmentsForCourse() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any())).thenReturn(
                AccessToken.builder().setAccessToken(token).build()
        );

        // Mock /classrooms
        String classroomsResponse = """
        [
          {
            "id": 123,
            "name": "My Course",
            "url": "https://classroom.github.com/classrooms/123"
          }
        ]
        """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(classroomsResponse)
                .setHeader("Content-Type", "application/json"));

        // Mock /classrooms/123/assignments
        String assignmentsResponse = """
        [
          {
            "id": "a1",
            "title": "Assignment 1",
            "slug": "assignment-1",
            "invite_link": "https://classroom.github.com/invite/test",
            "deadline": "2025-07-01T12:00:00Z"
          }
        ]
        """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(assignmentsResponse)
                .setHeader("Content-Type", "application/json"));

        // Mock /assignments/a1 (assignment details)
        String assignmentDetailsResponse = """
        {
          "starter_code_repository": {
            "full_name": "org-name/assignment-1"
          }
        }
        """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(assignmentDetailsResponse)
                .setHeader("Content-Type", "application/json"));

        // Mock /repos/org-name/assignment-1/readme (README content)
        mockWebServer.enqueue(new MockResponse()
                .setBody("<h1>README</h1>")
                .setHeader("Content-Type", "application/vnd.github.html+json"));

        when(assignmentRepository.existsByExternalId("a1")).thenReturn(false);

        githubClassroom.syncAssignmentsForCourse("My Course", user);

        // Verify the assignment was saved
        ExternalCodeAssignmentEntity expected = ExternalCodeAssignmentEntity.builder()
                .primaryKey(new ExternalCodeAssignmentEntity.PrimaryKey("My Course", "Assignment 1"))
                .externalId("a1")
                .assignmentLink("https://classroom.github.com/classrooms/123/assignments/assignment-1")
                .invitationLink("https://classroom.github.com/invite/test")
                .dueDate(OffsetDateTime.parse("2025-07-01T12:00:00Z"))
                .readmeHtml("<h1>README</h1>")
                .build();

        org.mockito.Mockito.verify(externalCodeAssignmentRepository)
                .save(org.mockito.ArgumentMatchers.refEq(expected));
    }

    @Test
    void testSyncAssignmentsForCourse_deletesMissingAssignments() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any())).thenReturn(
                AccessToken.builder().setAccessToken(token).build()
        );

        // Mock /classrooms
        mockWebServer.enqueue(new MockResponse().setBody("""
        [
          {
            "id": 123,
            "name": "My Course",
            "url": "https://classroom.github.com/classrooms/123"
          }
        ]
        """).setHeader("Content-Type", "application/json"));

        // Mock /classrooms/123/assignments - returns no assignments
        mockWebServer.enqueue(new MockResponse().setBody("[]")
                .setHeader("Content-Type", "application/json"));

        // Saved assignment not returned by GitHub anymore
        when(externalCodeAssignmentRepository.findAssignmentNamesByCourseTitle("My Course"))
                .thenReturn(List.of("Old Assignment"));

        githubClassroom.syncAssignmentsForCourse("My Course", user);

        ExternalCodeAssignmentEntity.PrimaryKey expectedPk =
                new ExternalCodeAssignmentEntity.PrimaryKey("My Course", "Old Assignment");

        org.mockito.Mockito.verify(externalCodeAssignmentRepository)
                .deleteById(eq(expectedPk));
    }

    @Test
    void testSyncAssignmentsForCourse_skipsExistingAssignment() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any())).thenReturn(
                AccessToken.builder().setAccessToken(token).build()
        );

        // Mock /classrooms
        mockWebServer.enqueue(new MockResponse().setBody("""
        [
          {
            "id": 123,
            "name": "My Course",
            "url": "https://classroom.github.com/classrooms/123"
          }
        ]
        """).setHeader("Content-Type", "application/json"));

        // Mock /classrooms/123/assignments
        mockWebServer.enqueue(new MockResponse().setBody("""
        [
          {
            "id": "a1",
            "title": "Assignment 1",
            "slug": "assignment-1",
            "invite_link": "https://classroom.github.com/invite/test",
            "deadline": null
          }
        ]
        """).setHeader("Content-Type", "application/json"));

        // This assignment already exists → skip
        when(assignmentRepository.existsByExternalId("a1")).thenReturn(true);

        githubClassroom.syncAssignmentsForCourse("My Course", user);

        // Should not save anything
        org.mockito.Mockito.verify(externalCodeAssignmentRepository, org.mockito.Mockito.never())
                .save(any());
    }



    @Test
    void testSyncGrades() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any())).thenReturn(
                AccessToken.builder().setAccessToken(token).build()
        );

        String gradesResponse = """
            [
              {
                "github_username": "alice",
                "points_awarded": 85.0,
                "points_available": 100.0,
                "submission_timestamp": "2025-07-10 14:00:00 UTC"
              },
              {
                "github_username": "bob",
                "points_awarded": 70.5,
                "points_available": 100.0,
                "submission_timestamp": "2025-07-11 09:30:00 UTC"
              }
            ]
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(gradesResponse)
                .setHeader("Content-Type", "application/json"));

        var results = githubClassroom.syncGrades("assignment-external-id", user);

        assertEquals(2, results.size());

        var alice = results.get(0);
        assertEquals("alice", alice.externalUsername());
        assertEquals(85.0, alice.achievedPoints());
        assertEquals(100.0, alice.totalPoints());

        var bob = results.get(1);
        assertEquals("bob", bob.externalUsername());
        assertEquals(70.5, bob.achievedPoints());
        assertEquals(100.0, bob.totalPoints());

        assertEquals(OffsetDateTime.parse("2025-07-10T14:00:00Z"), alice.date());
        assertEquals(OffsetDateTime.parse("2025-07-11T09:30:00Z"), bob.date());
    }

    @Test
    void testSyncGradeForStudent_success() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);
        when(userServiceClient.queryAccessToken(eq(user), any()))
                .thenReturn(AccessToken.builder()
                        .setAccessToken(token)
                        .build());

        // Step 1: Mock workflow runs
        mockWebServer.enqueue(new MockResponse().setBody("""
        {
          "workflow_runs": [
            {
              "status": "completed",
              "logs_url": "http://localhost:%d/logs",
              "updated_at": "2025-07-10T14:00:00Z"
            }
          ]
        }
        """.formatted(mockWebServer.getPort()))
                .setHeader("Content-Type", "application/json"));

        // Step 2: Mock logs ZIP with grading content
        byte[] zipBytes = TestUtils.createZipWithEntry(
                "autograding/run-autograding-tests.txt",
                """
                Processing: test-runner
                ✅ Test 1
                Test runner summary
                ┌────┬────────────┬──────┐
                │ #  │ Test        │ Score│
                ├────┼────────────┼──────┤
                │ 1  │ dummy      │ 10   │
                └────┴────────────┴──────┘
                {"totalPoints":10.0,"maxPoints":10.0}
                """
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(new Buffer().write(zipBytes))
                .setHeader("Content-Type", "application/zip"));

        ExternalGrading grading = githubClassroom.syncGradeForStudent("https://github.com/org-name/assignment-repo", user);

        assertEquals(10.0, grading.achievedPoints());
        assertEquals(10.0, grading.totalPoints());
        assertEquals("completed", grading.status());
    }

    @Test
    void testFindRepository_success() throws Exception {
        String token = "dummy-token";
        LoggedInUser user = mock(LoggedInUser.class);

        when(userServiceClient.queryAccessToken(eq(user), any()))
                .thenReturn(AccessToken.builder()
                        .setAccessToken(token)
                        .setExternalUserId("alice")
                        .build());

        String expectedUrl = "https://github.com/org-name/assignment-1-alice";

        mockWebServer.enqueue(new MockResponse()
                .setBody("""
        {
          "html_url": "%s"
        }
        """.formatted(expectedUrl)));

        String actualUrl = githubClassroom.findRepository("Assignment 1", "organizationName", user);

        assertEquals(expectedUrl, actualUrl);
    }
}
