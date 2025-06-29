package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;

import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@GraphQlApiTest
public class MutationUpdateAssignmentTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @Autowired
    private TestUtils testUtils;

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser =
            userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Test
    @Transactional
    @Commit
    void testUpdateRequiredPercentage(GraphQlTester tester) {
        AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        UUID assignmentId = assignmentEntity.getAssessmentId();
        double originalPercentage = assignmentEntity.getRequiredPercentage();
        double newPercentage = 0.8;

        final String mutation = """
            mutation($assessmentId: UUID!, $percentage: Float!) {
                updateAssignment(assessmentId: $assessmentId, input: {
                    requiredPercentage: $percentage
                }) {
                    assessmentId
                    requiredPercentage
                }
            }
        """;

        Assignment result = tester.document(mutation)
                .variable("assessmentId", assignmentId)
                .variable("percentage", newPercentage)
                .execute()
                .path("updateAssignment")
                .entity(Assignment.class)
                .get();

        assertThat(result.getRequiredPercentage(), is(not(originalPercentage)));
        assertThat(result.getRequiredPercentage(), is(newPercentage));
        assertThat(result.getAssessmentId(), is(assignmentId));
    }
}
