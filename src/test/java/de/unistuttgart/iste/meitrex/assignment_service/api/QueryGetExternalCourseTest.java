package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCodeAssessmentProviderConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCourseServiceClientConfig;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.Course;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@GraphQlApiTest
@ContextConfiguration(classes = {
        MockedCourseServiceClientConfig.class,
        MockedCodeAssessmentProviderConfig.class
})
class QueryGetExternalCourseTest {

    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private CodeAssessmentProvider codeAssessmentProvider;

    @Test
    void testGetExternalCourseFetchedFromProvider(GraphQlTester tester) throws CourseServiceConnectionException, ExternalPlatformConnectionException, UserServiceConnectionException {
        String courseTitle = "Test Course";
        String externalUrl = "https://external.provider.url";
        String organizationName = "TestOrg";

        // Mock course service
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

        // Mock external provider
        ExternalCourse externalCourse = new ExternalCourse(courseTitle, externalUrl, organizationName);
        when(codeAssessmentProvider.getExternalCourse(courseTitle, loggedInUser)).thenReturn(externalCourse);

        String query = """
                query($courseId: UUID!) {
                    getExternalCourse(courseId: $courseId) {
                        courseTitle
                        url
                        organizationName
                    }
                }
            """;

        ExternalCourse result = tester.document(query)
                .variable("courseId", courseId)
                .execute()
                .path("getExternalCourse")
                .entity(ExternalCourse.class)
                .get();

        assertThat(result.getCourseTitle()).isEqualTo(courseTitle);
        assertThat(result.getOrganizationName()).isEqualTo(organizationName);
        assertThat(result.getUrl()).isEqualTo(externalUrl);
    }
}
