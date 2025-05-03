package de.unistuttgart.iste.meitrex.assignment_service.api;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Grading;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@GraphQlApiTest
public class QueryGetGradingForAssignmentForStudentTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    @Autowired
    private GradingRepository gradingRepository;
    private final UUID courseId = UUID.randomUUID();

    @Autowired
    private AssignmentMapper assignmentMapper;

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;

    @Test
    void testQueryGetGradingValidFirstGrading(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final List<GradingEntity> originalGradingEntities = testUtils.populateGradingRepository(gradingRepository, assignmentEntity);

        final String query = """
                query($assignmentId: UUID!, $studentId: UUID!) {
                    getGradingForAssignmentForStudent(assessmentId: $assignmentId, studentId: $studentId) {
                        assessmentId
                        studentId
                        date
                        achievedCredits
                        exerciseGradings {
                            itemId
                            studentId
                            achievedCredits
                            subexerciseGradings {
                                itemId
                                studentId
                                achievedCredits
                            }
                        }
                    }
                }
                """;

        GradingEntity gradingEntity1 = originalGradingEntities.get(0);

        Grading receivedGradingEntity = tester.document(query)
                .variable("assignmentId", assignmentEntity.getAssessmentId())
                .variable("studentId", gradingEntity1.getPrimaryKey().getStudentId())
                .execute()
                .path("getGradingForAssignmentForStudent")
                .entity(Grading.class)
                .get();

        // times need to be adjusted because repository (presumably) rounds to milliseconds and converts to UTC
        gradingEntity1.setDate(gradingEntity1.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));
        receivedGradingEntity.setDate(receivedGradingEntity.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));


        assertThat(assignmentMapper.gradingEntityToDto(gradingEntity1), is(receivedGradingEntity));
    }


    @Test
    void testQueryGetGradingValidThirdGrading(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final List<GradingEntity> originalGradingEntities = testUtils.populateGradingRepository(gradingRepository, assignmentEntity);

        final String query = """
                query($assignmentId: UUID!, $studentId: UUID!) {
                    getGradingForAssignmentForStudent(assessmentId: $assignmentId, studentId: $studentId) {
                        assessmentId
                        studentId
                        date
                        achievedCredits
                        exerciseGradings {
                            itemId
                            studentId
                            achievedCredits
                            subexerciseGradings {
                                itemId
                                studentId
                                achievedCredits
                            }
                        }
                    }
                }
                """;

        GradingEntity gradingEntity3 = originalGradingEntities.get(2);

        Grading receivedGradingEntity = tester.document(query)
                .variable("assignmentId", assignmentEntity.getAssessmentId())
                .variable("studentId", gradingEntity3.getPrimaryKey().getStudentId())
                .execute()
                .path("getGradingForAssignmentForStudent")
                .entity(Grading.class)
                .get();


        // times need to be adjusted because repository (presumably) rounds to milliseconds and converts to UTC
        gradingEntity3.setDate(gradingEntity3.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));
        receivedGradingEntity.setDate(receivedGradingEntity.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));

        assertThat(assignmentMapper.gradingEntityToDto(gradingEntity3), is(receivedGradingEntity));
    }

}
