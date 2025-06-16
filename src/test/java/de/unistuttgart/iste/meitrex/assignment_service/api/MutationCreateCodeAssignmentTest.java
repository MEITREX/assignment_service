package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedContentServiceClientConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCourseServiceClientConfig;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.testutil.TestUsers;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.content_service.exception.ContentServiceConnectionException;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

@GraphQlApiTest
@ContextConfiguration(classes = {
        MockedCourseServiceClientConfig.class,
        MockedContentServiceClientConfig.class
})
public class MutationCreateCodeAssignmentTest {

    @InjectCurrentUserHeader
    private final LoggedInUser mockUser = TestUsers.userWithMembershipInCourseWithId(UUID.randomUUID(), LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private ContentServiceClient contentServiceClient;

    @Autowired
    private ExternalCodeAssignmentRepository externalCodeAssignmentRepository;

    @Test
    void testCreateCodeAssignment(GraphQlTester tester) throws CourseServiceConnectionException, ContentServiceConnectionException {
        UUID courseId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();

        when(courseServiceClient.queryCourseById(courseId)).thenReturn(
                Course.builder().setId(courseId).setTitle("Code Course").build()
        );

        AssignmentAssessment mockedAssessment = AssignmentAssessment.builder()
                .setId(assessmentId)
                .setAssessmentMetadata(
                        AssessmentMetadata.builder()
                                .setSkillPoints(50)
                                .setSkillTypes(List.of(SkillType.CREATE))
                                .setInitialLearningInterval(50)
                                .build()
                )
                .setItems(List.of())
                .setUserProgressData(new UserProgressData())
                .setMetadata(ContentMetadata.builder()
                        .setChapterId(UUID.randomUUID())
                        .setCourseId(courseId)
                        .setName("Code Assignment")
                        .setRewardPoints(50)
                        .setSuggestedDate(OffsetDateTime.now().plusDays(7))
                        .setType(ContentType.ASSIGNMENT)
                        .setTagNames(List.of("code", "assignment")).build()).build();

        when(contentServiceClient.queryContentsOfCourse(mockUser.getId(), courseId)).thenReturn(
                List.of(mockedAssessment)
        );

        OffsetDateTime dueDate = OffsetDateTime.now().plusDays(5);
        externalCodeAssignmentRepository.save(
                ExternalCodeAssignmentEntity.builder()
                        .primaryKey(new ExternalCodeAssignmentEntity.PrimaryKey("Code Course", "Code Assignment"))
                        .assignmentLink("https://github.com/org/classroom/assignment")
                        .invitationLink("https://github.com/org/classroom/invite")
                        .readmeHtml("<h1>Hello World</h1>")
                        .dueDate(dueDate)
                        .externalId("ext-12345")
                        .build()
        );



        String mutation = """
            mutation ($courseId: UUID!, $assessmentId: UUID!) {
                _internal_noauth_createAssignment(courseId: $courseId, assessmentId: $assessmentId, input: {
                    totalCredits: 0,
                    assignmentType: CODE_ASSIGNMENT,
                    description: "Code assignment test",
                    requiredPercentage: 0.5
                }) {
                    courseId
                    assessmentId
                    totalCredits
                    assignmentType
                    date
                    description
                    requiredPercentage
                    externalId
                    codeAssignmentMetadata {
                        assignmentLink
                        invitationLink
                        readmeHtml
                    }
                }
            }
        """;

        Assignment createdAssignment = tester.document(mutation)
                .variable("courseId", courseId)
                .variable("assessmentId", assessmentId)
                .execute()
                .path("_internal_noauth_createAssignment")
                .entity(Assignment.class)
                .get();

        assertThat(createdAssignment.getCourseId(), is(courseId));
        assertThat(createdAssignment.getAssessmentId(), is(assessmentId));
        assertThat(createdAssignment.getAssignmentType(), is(AssignmentType.CODE_ASSIGNMENT));
        assertThat(createdAssignment.getDescription(), is("Code assignment test"));
        assertThat(createdAssignment.getRequiredPercentage(), is(0.5));
        assertThat(createdAssignment.getExternalId(), is("ext-12345"));

        CodeAssignmentMetadata metadata = createdAssignment.getCodeAssignmentMetadata();
        assertThat(metadata.getAssignmentLink(), is("https://github.com/org/classroom/assignment"));
        assertThat(metadata.getInvitationLink(), is("https://github.com/org/classroom/invite"));
        assertThat(metadata.getReadmeHtml(), containsString("Hello World"));

        boolean externalAssignmentStillExists = externalCodeAssignmentRepository
                .findById(new ExternalCodeAssignmentEntity.PrimaryKey("Code Course", "Code Assignment"))
                .isPresent();

        assertThat("ExternalCodeAssignmentEntity should have been deleted after creation",
                externalAssignmentStillExists, is(false));

    }
}
