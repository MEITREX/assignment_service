package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Subexercise;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasItem;

@GraphQlApiTest
public class MutationUpdateSubexerciseTest {


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
    void testUpdateValidSubexercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        updateSubexercise(input: {
                            itemId: $subexerciseId,
                            totalSubexerciseCredits: 60,
                            number: "bb"
                        }) {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
                    }
                }
                """;


        final UUID assignmentId = assignmentEntity.getId();
        final double assignmentCredits = assignmentEntity.getTotalCredits();
        final UUID subexerciseId = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        // Execute the mutation and get Subexercise
        final Subexercise updatedSubexercise = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("subexerciseId", subexerciseId)
                .execute()
                .path("mutateAssignment.updateSubexercise")
                .entity(Subexercise.class)
                .get();


        assertThat(updatedSubexercise.getItemId(), is(subexerciseId));
        assertThat(updatedSubexercise.getTotalSubexerciseCredits(), closeTo(60, 0));
        assertThat(updatedSubexercise.getNumber(), is("bb"));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();
        final ExerciseEntity exerciseEntityFromRepo = exercisesFromRepo.getFirst();
        final SubexerciseEntity createdSubexerciseFromRepo = exerciseEntityFromRepo
                .getSubexercises().stream()
                .filter(subexercise -> subexercise.getId().equals(subexerciseId))
                .findFirst().get();

        assertThat(assignmentFromRepo.getTotalCredits(), is(assignmentCredits + 60 - 20)); // +60 for new, -20 for old
        assertThat(exerciseEntityFromRepo.getTotalExerciseCredits(), is(assignmentCredits + 60 - 20)); // same credits because there is only one exercise in the assignment

        assertThat(createdSubexerciseFromRepo.getItemId(), is(subexerciseId));
        assertThat(createdSubexerciseFromRepo.getTotalSubexerciseCredits(), closeTo(60, 0));
        assertThat(createdSubexerciseFromRepo.getNumber(), is("bb"));

        assertThat(exerciseEntityFromRepo.getSubexercises(), hasSize(2));
        assertThat(exerciseEntityFromRepo.getSubexercises(), hasItem(
                SubexerciseEntity.builder().itemId(subexerciseId).parentExercise(exerciseEntityFromRepo).totalSubexerciseCredits(60).number("bb").build()
        ));

        assertThat(exerciseEntityFromRepo.getSubexercises(), not(hasItem(
                SubexerciseEntity.builder().itemId(subexerciseId).parentExercise(exerciseEntityFromRepo).totalSubexerciseCredits(20).number("b").build()
        )));
    }

    @Test
    void testUpdateInvalidSubexerciseWrongSubexerciseId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        updateSubexercise(input: {
                            itemId: $subexerciseId,
                            totalSubexerciseCredits: 60,
                            number: "bb"
                        }) {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
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
    void testUpdateInvalidSubexerciseWrongAssignmentId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final String query = """
                mutation ($assignmentId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        updateSubexercise(input: {
                            itemId: $subexerciseId,
                            totalSubexerciseCredits: 60,
                            number: "bb"
                        }) {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
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
