package de.unistuttgart.iste.meitrex.assignment_service.api;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Exercise;
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

@GraphQlApiTest
public class MutationUpdateExerciseTest {

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
    void testUpdateValidExercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID exerciseId = assignmentEntity.getExercises().getFirst().getId();
        final UUID subexerciseId1 = assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getId();
        final UUID subexerciseId2 = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId1: UUID!, $subexerciseId2: UUID!) {
                    mutateAssignment (assessmentId: $assignmentId) {
                        updateExercise (input: {
                            itemId: $exerciseId,
                            totalExerciseCredits: 55,
                            subexercises: [
                            {
                                itemId: $subexerciseId1,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 30,
                                number: "a"
                            },
                            {
                                itemId: $subexerciseId2,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 25,
                                number: "b"
                            }
                            ],
                            number: "1"
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
        final Exercise updatedExercise = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", exerciseId)
                .variable("subexerciseId1", subexerciseId1)
                .variable("subexerciseId2", subexerciseId2)
                .execute()
                .path("mutateAssignment.updateExercise")
                .entity(Exercise.class)
                .get();


        assertThat(updatedExercise.getItemId(), is(exerciseId));
        assertThat(updatedExercise.getTotalExerciseCredits(), closeTo(55, 0));
        assertThat(updatedExercise.getSubexercises(), hasSize(2));
        assertThat(updatedExercise.getNumber(), is("1"));

        assertThat(updatedExercise.getSubexercises(), containsInAnyOrder(
                new Subexercise(subexerciseId1, 30, "a", null),
                new Subexercise(subexerciseId2, 25, "b", null)
        ));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();
        final ExerciseEntity updatedExerciseFromRepo = exercisesFromRepo.stream().filter(exercise -> exercise.getId().equals(exerciseId)).findFirst().get();

        assertThat(assignmentFromRepo.getTotalCredits(), is(assignmentCredits + 5));
        assertThat(updatedExerciseFromRepo.getItemId(), is(exerciseId));
        assertThat(updatedExerciseFromRepo.getTotalExerciseCredits(), closeTo(55, 0));
        assertThat(updatedExerciseFromRepo.getSubexercises(), hasSize(2));
        assertThat(updatedExerciseFromRepo.getNumber(), is("1"));

        assertThat(updatedExerciseFromRepo.getSubexercises(), containsInAnyOrder(
                SubexerciseEntity.builder().itemId(subexerciseId1).totalSubexerciseCredits(30).number("a").parentExercise(updatedExerciseFromRepo).build(),
                SubexerciseEntity.builder().itemId(subexerciseId2).totalSubexerciseCredits(25).number("b").parentExercise(updatedExerciseFromRepo).build()
        ));

    }

    /**
     * Given an assignment
     * When the "updateExercise" mutation is called with a new exercise where subexerciseCredits don't add up
     * Then an exception is thrown
     */
    @Test
    void testUpdateInvalidExerciseIncorrectSum(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID exerciseId = assignmentEntity.getExercises().getFirst().getId();
        final UUID subexerciseId1 = assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getId();
        final UUID subexerciseId2 = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId1: UUID!, $subexerciseId2: UUID!) {
                    mutateAssignment (assessmentId: $assignmentId) {
                        updateExercise (input: {
                            itemId: $exerciseId,
                            totalExerciseCredits: 60,
                            subexercises: [
                            {
                                itemId: $subexerciseId1,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 30,
                                number: "a"
                            },
                            {
                                itemId: $subexerciseId2,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 25,
                                number: "b"
                            }
                            ],
                            number: "1"
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

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", exerciseId)
                .variable("subexerciseId1", subexerciseId1)
                .variable("subexerciseId2", subexerciseId2)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains("Subexercise credits do not sum up to total exercise credits."));

    }

    /**
     * Given an assignment
     * When the "updateExercise" mutation is called with a new exercise where the exerciseId is wrong
     * Then an exception is thrown
     */
    @Test
    void testUpdateInvalidExerciseWrongId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);

        final UUID subexerciseId1 = assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getId();
        final UUID subexerciseId2 = assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId1: UUID!, $subexerciseId2: UUID!) {
                    mutateAssignment (assessmentId: $assignmentId) {
                        updateExercise (input: {
                            itemId: $exerciseId,
                            totalExerciseCredits: 55,
                            subexercises: [
                            {
                                itemId: $subexerciseId1,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 30,
                                number: "a"
                            },
                            {
                                itemId: $subexerciseId2,
                                parentExerciseId: $exerciseId,
                                totalSubexerciseCredits: 25,
                                number: "b"
                            }
                            ],
                            number: "1"
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
        final UUID wrongId = UUID.randomUUID();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("exerciseId", wrongId)
                .variable("subexerciseId1", subexerciseId1)
                .variable("subexerciseId2", subexerciseId2)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Exercise with itemId %s not found in assignmentEntity", wrongId)));

    }

}