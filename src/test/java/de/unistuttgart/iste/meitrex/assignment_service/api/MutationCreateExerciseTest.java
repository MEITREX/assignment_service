package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser.UserRoleInCourse;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.generated.dto.Exercise;
import de.unistuttgart.iste.meitrex.generated.dto.Subexercise;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.UUID;

@GraphQlApiTest
public class MutationCreateExerciseTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;


    @Test
    @Transactional
    @Commit
    void testCreateValidExercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID itemId1 = UUID.randomUUID();
        final UUID itemId2 = UUID.randomUUID();
        final UUID itemId3 = UUID.randomUUID();

        final String query = """
                mutation ($assignmentId: UUID!, $itemId1: UUID!, $itemId2: UUID!, $itemId3: UUID!) {
                  mutateAssignment(assessmentId: $assignmentId) {
                    createExercise(input: {
                        itemId: $itemId1,
                        totalExerciseCredits: 25,
                        subexercises: [
                        {
                            itemId: $itemId2,
                            parentExerciseId: $itemId1,
                            totalSubexerciseCredits: 20,
                            number: "2a"
                        },
                        {
                            itemId: $itemId3,
                            parentExerciseId: $itemId1,
                            totalSubexerciseCredits: 5,
                            number: "2b"
                        }
                        ],
                        number: "2"
                    }) {
                        itemId
                        totalExerciseCredits
                        subexercises {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
                        number
                    }
                  }
                }
                """;

        final UUID assignmentId = assignmentEntity.getId();
        final double assignmentCredits = assignmentEntity.getTotalCredits();

        // Execute the mutation and get Exercise
        final Exercise createdExercise = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("itemId1", itemId1)
                .variable("itemId2", itemId2)
                .variable("itemId3", itemId3)
                .execute()
                .path("mutateAssignment.createExercise")
                .entity(Exercise.class)
                .get();

        assertThat(createdExercise.getItemId(), is(itemId1));
        assertThat(createdExercise.getTotalExerciseCredits(), closeTo(25, 0));
        assertThat(createdExercise.getSubexercises(), hasSize(2));
        assertThat(createdExercise.getNumber(), is("2"));

        assertThat(createdExercise.getSubexercises(), containsInAnyOrder(
                new Subexercise(itemId2, 20, "2a", null),
                new Subexercise(itemId3, 5, "2b", null)
        ));

        // assert that exercise was added to the assignment in the repository
        assertThat(assignmentRepository.getReferenceById(assignmentId).getExercises().stream().map(ExerciseEntity::getId).toList(), hasItem(itemId1));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();
        final ExerciseEntity createdExerciseFromRepo = exercisesFromRepo.stream().filter(exercise -> exercise.getId().equals(itemId1)).findFirst().get();

        assertThat(assignmentFromRepo.getTotalCredits(), is(assignmentCredits + 25));
        assertThat(createdExerciseFromRepo.getItemId(), is(itemId1));
        assertThat(createdExerciseFromRepo.getTotalExerciseCredits(), closeTo(25, 0));
        assertThat(createdExerciseFromRepo.getSubexercises(), hasSize(2));
        assertThat(createdExerciseFromRepo.getNumber(), is("2"));

        assertThat(createdExerciseFromRepo.getSubexercises(), containsInAnyOrder(
                SubexerciseEntity.builder().itemId(itemId2).totalSubexerciseCredits(20).number("2a").parentExercise(createdExerciseFromRepo).build(),
                SubexerciseEntity.builder().itemId(itemId3).totalSubexerciseCredits(5).number("2b").parentExercise(createdExerciseFromRepo).build()
        ));
    }

    @Test
    void testCreateInvalidExerciseIncorrectSum(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID itemId1 = UUID.randomUUID();
        final UUID itemId2 = UUID.randomUUID();
        final UUID itemId3 = UUID.randomUUID();

        final String query = """
                mutation ($assignmentId: UUID!, $itemId1: UUID!, $itemId2: UUID!, $itemId3: UUID!) {
                  mutateAssignment(assessmentId: $assignmentId) {
                    createExercise(input: {
                        itemId: $itemId1,
                        totalExerciseCredits: 70,
                        subexercises: [
                        {
                            itemId: $itemId2,
                            parentExerciseId: $itemId1,
                            totalSubexerciseCredits: 20,
                            number: "2a"
                        },
                        {
                            itemId: $itemId3,
                            parentExerciseId: $itemId1,
                            totalSubexerciseCredits: 5,
                            number: "2b"
                        }
                        ],
                        number: "2"
                    }) {
                        itemId
                        totalExerciseCredits
                        subexercises {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
                        number
                    }
                  }
                }
                """;

        final UUID assignmentId = assignmentEntity.getId();

        // Execute the mutation and expect validation exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("itemId1", itemId1)
                .variable("itemId2", itemId2)
                .variable("itemId3", itemId3)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains("Subexercise credits do not sum up to total exercise credits."));

    }
}
