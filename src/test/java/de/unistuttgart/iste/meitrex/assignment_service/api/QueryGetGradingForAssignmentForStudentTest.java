package de.unistuttgart.iste.meitrex.assignment_service.api;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;

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
                        userId
                        date
                        achievedCredits
                        exerciseGradings {
                            itemId
                            userId
                            achievedCredits
                            subexerciseGradings {
                                itemId
                                userId
                                achievedCredits
                            }
                        }
                    }
                }
                """;

        GradingEntity gradingEntity1 = originalGradingEntities.get(0);

        GradingEntity receivedGradingEntity = tester.document(query)
                .variable("assignmentId", assignmentEntity.getAssessmentId())
                .variable("studentId", gradingEntity1.getPrimaryKey().getStudentId())
                .execute()
                .path("getGradingForAssignmentForStudent")
                .entity(GradingEntity.class)
                .get();

        assertThat(gradingEntity1, is(receivedGradingEntity));
    }


    @Test
    void testQueryGetGradingValidThirdGrading(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final List<GradingEntity> originalGradingEntities = testUtils.populateGradingRepository(gradingRepository, assignmentEntity);

        final String query = """
                query($assignmentId: UUID!, $studentId: UUID!) {
                    getGradingForAssignmentForStudent(assessmentId: $assignmentId, studentId: $studentId) {
                        assessmentId
                        userId
                        date
                        achievedCredits
                        exerciseGradings {
                            itemId
                            userId
                            achievedCredits
                            subexerciseGradings {
                                itemId
                                userId
                                achievedCredits
                            }
                        }
                    }
                }
                """;

        GradingEntity gradingEntity3 = originalGradingEntities.get(2);

        GradingEntity receivedGradingEntity = tester.document(query)
                .variable("assignmentId", assignmentEntity.getAssessmentId())
                .variable("studentId", gradingEntity3.getPrimaryKey().getStudentId())
                .execute()
                .path("getGradingForAssignmentForStudent")
                .entity(GradingEntity.class)
                .get();

        assertThat(gradingEntity3, is(receivedGradingEntity));
    }

}
