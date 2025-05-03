package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@GraphQlApiTest
public class QueryFindAssignmentsTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @Autowired
    private AssignmentMapper assignmentMapper;

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;

    @Test
    void testQueryFindAssignments(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                query($ids: [UUID!]!) {
                    findAssignmentsByAssessmentIds (assessmentIds: $ids) {
                        assessmentId
                        courseId
                        exercises {
                            itemId
                            totalExerciseCredits
                            subexercises {
                                itemId
                                totalSubexerciseCredits
                                number
                            }
                            number
                        }
                        date
                        totalCredits
                        assignmentType
                        description
                        requiredPercentage
                    }
                }
                """;

        List<Assignment> assignmentList = tester.document(query)
                .variable("ids", List.of(assignmentEntity.getId()))
                .execute()
                .path("findAssignmentsByAssessmentIds")
                .entityList(Assignment.class)
                .hasSize(1)
                .get();

        assertThat(assignmentList, contains(assignmentMapper.assignmentEntityToDto(assignmentEntity)));

    }


    @Test
    void testQueryFindAssignmentsWrongList(final GraphQlTester tester) {
        testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                query($ids: [UUID!]!) {
                    findAssignmentsByAssessmentIds (assessmentIds: $ids) {
                        assessmentId
                        courseId
                        exercises {
                            itemId
                            totalExerciseCredits
                            subexercises {
                                itemId
                                totalSubexerciseCredits
                                number
                            }
                            number
                        }
                        date
                        totalCredits
                        assignmentType
                        description
                        requiredPercentage
                    }
                }
                """;

        List<Assignment> assignments = tester.document(query)
                .variable("ids", List.of(UUID.randomUUID(), UUID.randomUUID()))
                .execute()
                .path("findAssignmentsByAssessmentIds")
                .entityList(Assignment.class)
                .get();

        assertThat(assignments.get(0), is(nullValue()));
        assertThat(assignments.get(1), is(nullValue()));
    }

}
