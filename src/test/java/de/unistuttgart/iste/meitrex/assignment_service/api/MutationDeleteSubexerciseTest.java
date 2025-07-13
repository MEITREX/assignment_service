package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@GraphQlApiTest
class MutationDeleteSubexerciseTest {

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
    void testDeleteValidSubexercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        deleteSubexercise(itemId: $subexerciseId)
                    }
                }
                """;


        final UUID assignmentId = assignmentEntity.getId();
        final double assignmentCredits = assignmentEntity.getTotalCredits();
        final UUID subexerciseId = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        // Execute the mutation and get UUID
        final UUID deletedId = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("subexerciseId", subexerciseId)
                .execute()
                .path("mutateAssignment.deleteSubexercise")
                .entity(UUID.class)
                .get();

        assertThat(deletedId, is(subexerciseId));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();
        final List<SubexerciseEntity> subexercisesFromRepo = exercisesFromRepo.getFirst().getSubexercises();

        assertThat(assignmentFromRepo.getTotalCredits(), is(assignmentCredits - 20)); // 20 is number of credits in second (i.e. the deleted) subexercise
        assertThat(exercisesFromRepo, hasSize(1));
        assertThat(exercisesFromRepo.getFirst().getTotalExerciseCredits(), is(assignmentCredits - 20)); // same credits because there is only one exercise in the assignment
        assertThat(subexercisesFromRepo, hasSize(1));
    }

    @Test
    void testDeleteInvalidSubexerciseWrongSubexerciseId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        deleteSubexercise(itemId: $subexerciseId)
                    }
                }
                """;


        final UUID assignmentId = assignmentEntity.getId();
        final UUID wrongId = UUID.randomUUID();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("subexerciseId", wrongId)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Subexercise with itemId %s not found", wrongId)));

    }


    @Test
    void testDeleteInvalidSubexerciseWrongAssignmentId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        deleteSubexercise(itemId: $subexerciseId)
                    }
                }
                """;

        final UUID wrongId = UUID.randomUUID();
        final UUID subexerciseId = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", wrongId)
                .variable("subexerciseId", subexerciseId)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Assignment with assessmentId %s not found", wrongId)));

    }

}
