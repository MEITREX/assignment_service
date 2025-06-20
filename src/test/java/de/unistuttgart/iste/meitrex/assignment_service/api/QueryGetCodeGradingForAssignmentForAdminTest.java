package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCodeAssessmentProviderConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedCourseServiceClientConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedUserServiceClientConfig;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
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
import de.unistuttgart.iste.meitrex.generated.dto.ExternalUserIdWithUser;
import de.unistuttgart.iste.meitrex.generated.dto.Grading;
import de.unistuttgart.iste.meitrex.generated.dto.UserInfo;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalServiceProviderDto;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.ExternalGrading;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.is;


@GraphQlApiTest
@ContextConfiguration(classes = {MockedCodeAssessmentProviderConfig.class, MockedUserServiceClientConfig.class, MockedCourseServiceClientConfig.class})
public class QueryGetCodeGradingForAssignmentForAdminTest {

    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private GradingRepository gradingRepository;

    @Autowired
    private CodeAssessmentProvider codeAssessmentProvider;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private TestUtils testUtils;

    @Test
    @Transactional
    void testAdminGetsCodeAssignmentGradingsSynced(GraphQlTester tester) throws ExternalPlatformConnectionException, UserServiceConnectionException, CourseServiceConnectionException {
        AssignmentEntity codeAssignmentEntity = testUtils.populateAssignmentRepositoryWithCodeAssignment(assignmentRepository, courseId);

        UUID studentId = UUID.randomUUID();
        GradingEntity gradingEntity = testUtils.populateGradingRepositoryForCodeAssignment(gradingRepository, codeAssignmentEntity, studentId).get(0);

        ExternalGrading externalGrading = new ExternalGrading("external-user", null,
                OffsetDateTime.now(), null, 50.0, 60.0);

        when(codeAssessmentProvider.syncGrades(codeAssignmentEntity.getExternalId(), loggedInUser))
                .thenReturn(List.of(externalGrading));

        when(userServiceClient.queryExternalUserIds(ExternalServiceProviderDto.GITHUB, List.of(studentId)))
                .thenReturn(List.of(new ExternalUserIdWithUser(studentId, "external-user")));

        when(codeAssessmentProvider.getName()).thenReturn(ExternalServiceProviderDto.GITHUB);

        when(courseServiceClient.queryMembershipsInCourse(any(UUID.class)))
                .thenReturn(List.of());

        when(codeAssessmentProvider.syncGradeForStudent("link", loggedInUser))
                .thenReturn(externalGrading);

        UserInfo user = UserInfo.builder()
                .setId(studentId)
                .setUserName("user")
                .setFirstName("first")
                .setLastName("last")
                .setRealmRoles(List.of())
                .build();

        doReturn(List.of(user)).when(userServiceClient).queryUserInfos(any());

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
                .variable("assignmentId", codeAssignmentEntity.getAssessmentId())
                .execute()
                .path("getGradingsForAssignment")
                .entityList(Grading.class)
                .get();

        Grading expected = assignmentMapper.gradingEntityToDto(gradingRepository.findById(gradingEntity.getId()).orElseThrow());
        Grading actual = results.get(0);
        assertThat(actual.getAssessmentId(), is(expected.getAssessmentId()));
        assertThat(actual.getStudentId(), is(expected.getStudentId()));
        assertThat(actual.getAchievedCredits(), is(externalGrading.achievedPoints()));

        AssignmentEntity updatedAssignment = assignmentRepository.findById(codeAssignmentEntity.getAssessmentId()).orElseThrow();
        assertThat(updatedAssignment.getTotalCredits(), is(externalGrading.totalPoints()));
    }
}
