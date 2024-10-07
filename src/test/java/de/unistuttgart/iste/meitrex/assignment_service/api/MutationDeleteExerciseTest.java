package de.unistuttgart.iste.meitrex.assignment_service.api;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@GraphQlApiTest
public class MutationDeleteExerciseTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;


    @Test
    @Transactional
    @Commit
    void testDeleteValidExercise(final GraphQlTester tester){
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

        // Execute the mutation and get UUID
        final UUID deletedId = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", exerciseId)
                .execute()
                .path("mutateAssignment.deleteExercise")
                .entity(UUID.class)
                .get();

        assertThat(deletedId, is(exerciseId));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();

        assertThat(assignmentFromRepo.getTotalCredits(), is(0.0));
        assertThat(exercisesFromRepo.isEmpty(), is(true));
    }

    @Test
    void testDeleteInvalidExerciseWrongId(final GraphQlTester tester){
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!) {
                    mutateAssignment (assessmentId: $assignmentId) {
                        deleteExercise (itemId: $exerciseId)
                    }
                }
                """;

        final UUID assignmentId = assignmentEntity.getId();
        final UUID wrongId = UUID.randomUUID();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", wrongId)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Exercise with itemId %s not found", wrongId)));


    }

}
