package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExternalCourseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCourseServiceClientConfig;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.Course;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = MockedCourseServiceClientConfig.class)
@GraphQlApiTest
public class QueryGetExternalCodeAssignmentsTest {

    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private ExternalCodeAssignmentRepository externalCodeAssignmentRepository;


    @Test
    @Transactional
    @Commit
    void testGetExternalCodeAssignments(GraphQlTester tester) throws CourseServiceConnectionException {
        String courseTitle = "Test Course";
        Course mockCourse = Course.builder()
                .setId(courseId)
                .setTitle(courseTitle)
                .setDescription("mock")
                .setStartDate(OffsetDateTime.now())
                .setEndDate(OffsetDateTime.now().plusDays(30))
                .setPublished(true)
                .setMemberships(List.of())
                .build();

        when(courseServiceClient.queryCourseById(courseId)).thenReturn(mockCourse);

        externalCodeAssignmentRepository.saveAll(List.of(
                ExternalCodeAssignmentEntity.builder()
                        .primaryKey(new ExternalCodeAssignmentEntity.PrimaryKey(
                                courseTitle,
                                "assignment-a"
                        ))
                        .assignmentLink("assignment-a-link")
                        .invitationLink("assignment-a-invitation-link")
                        .externalId("assignment-a-id")
                        .build(),
                ExternalCodeAssignmentEntity.builder()
                        .primaryKey(new ExternalCodeAssignmentEntity.PrimaryKey(
                                courseTitle,
                                "assignment-b"
                        ))
                        .assignmentLink("assignment-b-link")
                        .invitationLink("assignment-b-invitation-link")
                        .externalId("assignment-b-id")
                        .build()
        ));

        String query = """
                query($courseId: UUID!) {
                    getExternalCodeAssignments(courseId: $courseId)
                }
            """;

        List<String> result = tester.document(query)
                .variable("courseId", courseId)
                .execute()
                .path("getExternalCodeAssignments")
                .entityList(String.class)
                .get();

        assertThat(result).containsExactlyInAnyOrder("assignment-a", "assignment-b");
    }
}
