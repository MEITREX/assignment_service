package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalAssignment;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;

@GraphQlApiTest
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class QueryGetExternalAssignmentsTest {


    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);


    @Test
    void testQueryGetExternalAssignments(final GraphQlTester tester) {
        final String query = """
                query($courseId: UUID!) {
                    getExternalAssignments (courseId: $courseId) {
                        externalId,
                        sheetNo
                    }
                }
                """;


        List<ExternalAssignment> externalAssignmentList = tester.document(query)
                .variable("courseId", courseId)
                .execute()
                .path("getExternalAssignments")
                .entityList(ExternalAssignment.class)
                .get();

        List<ExternalAssignment> expectedExternalAssignments = List.of(
                new ExternalAssignment("657b4ae4-e341-441e-87b1-46b3306c5ef0", 1.0),
                new ExternalAssignment("929af54d-0f33-48e6-b05d-1b9cfdc7e0a3", 2.0)
        );

        assertThat(externalAssignmentList, Matchers.equalTo(expectedExternalAssignments));
    }


}
