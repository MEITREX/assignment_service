package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.AuthorizationAsserts;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;

@GraphQlApiTest
public class AuthorizationTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.STUDENT);

    @Autowired
    private TestUtils testUtils;

    /**
     * Given an assignment and a student user
     * When using the deleteExercise mutation
     * Then there will be an authorization error
     */
    @Test
    void testAuthorizationDeleteExercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID exerciseId = assignmentEntity.getExercises().getFirst().getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!) {
                    mutateAssignment (assessmentId: $assignmentId) {
                        deleteExercise (itemId: $exerciseId)
                    }
                }
                """;

        final UUID assignmentId = assignmentEntity.getId();

        // Execute the mutation and expect authorization issue
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", exerciseId)
                .execute()
                .errors()
                .satisfy(AuthorizationAsserts::assertIsMissingUserRoleError);
    }
}
