package de.unistuttgart.iste.meitrex.assignment_service.api;


import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExternalCourseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCourseRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.*;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.content_service.exception.ContentServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.ExternalGrading;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

@GraphQlApiTest
@ContextConfiguration(classes = {
        MockedCodeAssessmentProviderConfig.class,
        MockedUserServiceClientConfig.class,
        MockedCourseServiceClientConfig.class,
        MockedContentServiceClientConfig.class,
        MockedTopicPublisherConfig.class
})
class QueryGetCodeGradingForAssignmentForStudentTest {

    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.STUDENT);

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private GradingRepository gradingRepository;

    @Autowired
    private CodeAssessmentProvider codeAssessmentProvider;

    @Autowired
    private ContentServiceClient contentServiceClient;

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private TestUtils testUtils;

    @Autowired
    private TopicPublisher topicPublisher;
    @Autowired
    private ExternalCourseRepository externalCourseRepository;

    @Test
    @Transactional
    void testStudentGetsCodeAssignmentGradingSynced(GraphQlTester tester) throws ExternalPlatformConnectionException, ContentServiceConnectionException, UserServiceConnectionException {
        final UUID studentId = loggedInUser.getId();
        AssignmentEntity assignment = testUtils.populateAssignmentRepositoryWithCodeAssignment(assignmentRepository, courseId);

        GradingEntity gradingEntity = testUtils.populateGradingRepositoryForCodeAssignment(gradingRepository, assignment, studentId).get(0);

        AssignmentAssessment mockedAssessment = AssignmentAssessment.builder()
                .setId(assignment.getAssessmentId())
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

        when(contentServiceClient.queryContentsOfCourse(studentId, courseId)).thenReturn(
                List.of(mockedAssessment)
        );

        ExternalGrading externalGrading = new ExternalGrading("ext-user", gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink(), OffsetDateTime.now(),
                "<table>feedback</table>", 42.0, 60.0, "test-commit-sha");

        when(codeAssessmentProvider.findRepository(eq(assignment.getExternalId()), any(), any())).thenReturn(gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink());

        when(codeAssessmentProvider.syncGradeForStudent(eq(gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink()), any())).thenReturn(externalGrading);

        String query = """
                query($assignmentId: UUID!) {
                    getGradingsForAssignment(assessmentId: $assignmentId) {
                        assessmentId
                        studentId
                        achievedCredits
                        date
                    }
                }
                """;

        List<Grading> results = tester.document(query)
                .variable("assignmentId", assignment.getAssessmentId())
                .execute()
                .path("getGradingsForAssignment")
                .entityList(Grading.class)
                .get();

        Grading actual = results.get(0);
        assertThat(actual.getStudentId(), is(studentId));
        assertThat(actual.getAchievedCredits(), is(42.0));
        assertThat(actual.getAssessmentId(), is(assignment.getAssessmentId()));

        AssignmentEntity updatedAssignment = assignmentRepository.findById(assignment.getAssessmentId()).orElseThrow();
        assertThat(updatedAssignment.getTotalCredits(), is(60.0));

        ArgumentCaptor<ContentProgressedEvent> captor = ArgumentCaptor.forClass(ContentProgressedEvent.class);
        verify(topicPublisher, atLeastOnce()).notifyUserWorkedOnContent(captor.capture());

        List<ContentProgressedEvent> allEvents = captor.getAllValues();
        ContentProgressedEvent event = allEvents.stream()
                .filter(e -> e.getUserId().equals(studentId) &&
                        e.getContentId().equals(assignment.getAssessmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ContentProgressedEvent not published"));


        double expectedCorrectness = 42.0 / 60.0;
        boolean expectedSuccess = 42.0 >= (updatedAssignment.getRequiredPercentage() == null ? 0.5 : updatedAssignment.getRequiredPercentage()) * 60.0;

        assertThat(event.getUserId(), is(studentId));
        assertThat(event.getContentId(), is(assignment.getAssessmentId()));
        assertThat(event.getCorrectness(), is(expectedCorrectness));
        assertThat(event.isSuccess(), is(expectedSuccess));
        assertThat(event.getResponses().isEmpty(), is(true));
    }

    @Test
    @Transactional
    void testStudentGetsCodeAssignmentGradingCreated(GraphQlTester tester)
            throws ExternalPlatformConnectionException, ContentServiceConnectionException, UserServiceConnectionException, CourseServiceConnectionException {

        final UUID studentId = loggedInUser.getId();
        AssignmentEntity assignment = testUtils.populateAssignmentRepositoryWithCodeAssignment(assignmentRepository, courseId);

        gradingRepository.deleteAll();

        String assignmentName = "Generated Code Assignment Name";

        AssignmentAssessment mockedAssessment = AssignmentAssessment.builder()
                .setId(assignment.getAssessmentId())
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
                        .setName(assignmentName)
                        .setRewardPoints(50)
                        .setSuggestedDate(OffsetDateTime.now().plusDays(7))
                        .setType(ContentType.ASSIGNMENT)
                        .setTagNames(List.of("code", "assignment")).build()).build();

        when(contentServiceClient.queryContentsOfCourse(studentId, courseId)).thenReturn(List.of(mockedAssessment));

        Course mockCourse = Course.builder()
                .setId(assignment.getCourseId())
                .setTitle("courseTitle")
                .build();

        when(courseServiceClient.queryCourseById(eq(assignment.getCourseId())))
                .thenReturn(mockCourse);

        externalCourseRepository.save(
                new ExternalCourseEntity("courseTitle", "https://external.provider.url", "TestOrg")
        );

        when(codeAssessmentProvider.findRepository(
                eq(assignmentName),
                eq("TestOrg"),
                any(LoggedInUser.class)))
                .thenReturn("https://github.com/user/repo");

        ExternalGrading externalGrading = new ExternalGrading(
                "ext-student-id",
                "https://github.com/user/repo",
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                "<table>feedback</table>",
                35.0,
                50.0,
                "test-commit-sha-2"
        );
        when(codeAssessmentProvider.syncGradeForStudent(eq("https://github.com/user/repo"), any()))
                .thenReturn(externalGrading);

        String query = """
            query($assignmentId: UUID!) {
                getGradingsForAssignment(assessmentId: $assignmentId) {
                    assessmentId
                    studentId
                    achievedCredits
                    date
                }
            }
            """;

        List<Grading> results = tester.document(query)
                .variable("assignmentId", assignment.getAssessmentId())
                .execute()
                .path("getGradingsForAssignment")
                .entityList(Grading.class)
                .get();

        Grading actual = results.get(0);
        assertThat(actual.getStudentId(), is(studentId));
        assertThat(actual.getAchievedCredits(), is(35.0));
        assertThat(actual.getAssessmentId(), is(assignment.getAssessmentId()));

        AssignmentEntity updatedAssignment = assignmentRepository.findById(assignment.getAssessmentId()).orElseThrow();
        assertThat(updatedAssignment.getTotalCredits(), is(50.0));

        ArgumentCaptor<ContentProgressedEvent> captor = ArgumentCaptor.forClass(ContentProgressedEvent.class);
        verify(topicPublisher, atLeastOnce()).notifyUserWorkedOnContent(captor.capture());

        List<ContentProgressedEvent> allEvents = captor.getAllValues();
        ContentProgressedEvent event = allEvents.stream()
                .filter(e -> e.getUserId().equals(studentId) &&
                        e.getContentId().equals(assignment.getAssessmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ContentProgressedEvent not published"));

        double expectedCorrectness = 35.0 / 50.0;
        boolean expectedSuccess = 35.0 >= (updatedAssignment.getRequiredPercentage() == null ? 0.5 : updatedAssignment.getRequiredPercentage()) * 50.0;

        assertThat(event.getUserId(), is(studentId));
        assertThat(event.getContentId(), is(assignment.getAssessmentId()));
        assertThat(event.getCorrectness(), is(expectedCorrectness));
        assertThat(event.isSuccess(), is(expectedSuccess));
        assertThat(event.getResponses().isEmpty(), is(true));
    }

}