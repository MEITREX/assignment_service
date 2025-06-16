package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCodeAssessmentProviderConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCourseServiceClientConfig;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.Course;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.mockito.Mockito.when;

@GraphQlApiTest
@ContextConfiguration(classes = {MockedCodeAssessmentProviderConfig.class, MockedCourseServiceClientConfig.class})
public class MutationSyncAssignmentsTest {

    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private CodeAssessmentProvider codeAssessmentProvider;

    @Test
    void testSyncAssignmentsSuccess(GraphQlTester tester) throws Exception {
        String courseTitle = "Test Course";

        Course mockCourse = Course.builder()
                .setId(courseId)
                .setTitle(courseTitle)
                .setDescription("desc")
                .setStartDate(OffsetDateTime.now())
                .setEndDate(OffsetDateTime.now().plusDays(5))
                .setPublished(true)
                .setMemberships(List.of())
                .build();

        when(courseServiceClient.queryCourseById(courseId)).thenReturn(mockCourse);

        String mutation = """
                mutation($courseId: UUID!) {
                    syncAssignmentsForCourse(courseId: $courseId)
                }
            """;

        tester.document(mutation)
                .variable("courseId", courseId)
                .execute()
                .path("syncAssignmentsForCourse")
                .entity(Boolean.class)
                .isEqualTo(true);

        Mockito.verify(codeAssessmentProvider).syncAssignmentsForCourse(courseTitle, loggedInUser);
    }

    @Test
    void testSyncAssignmentsFails(GraphQlTester tester) throws Exception {
        when(courseServiceClient.queryCourseById(courseId)).thenThrow(new RuntimeException("boom"));

        String mutation = """
                mutation($courseId: UUID!) {
                    syncAssignmentsForCourse(courseId: $courseId)
                }
            """;

        tester.document(mutation)
                .variable("courseId", courseId)
                .execute()
                .path("syncAssignmentsForCourse")
                .entity(Boolean.class)
                .isEqualTo(false);
    }
}
