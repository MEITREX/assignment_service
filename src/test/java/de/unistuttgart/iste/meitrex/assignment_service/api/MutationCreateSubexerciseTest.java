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
import static org.hamcrest.Matchers.is;

@GraphQlApiTest
public class MutationCreateSubexerciseTest {

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
    void testCreateValidSubexercise(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID subexerciseId = UUID.randomUUID();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        createSubexercise(input: {
                            itemId: $subexerciseId,
                            parentExerciseId: $exerciseId,
                            totalSubexerciseCredits: 60,
                            number: "c"
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
        final UUID exerciseId = assignmentEntity.getExercises().getFirst().getId();

        // Execute the mutation and get Subexercise
        final Subexercise createdSubexercise = tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("subexerciseId", subexerciseId)
                .variable("exerciseId", exerciseId)
                .execute()
                .path("mutateAssignment.createSubexercise")
                .entity(Subexercise.class)
                .get();


        assertThat(createdSubexercise.getItemId(), is(subexerciseId));
        assertThat(createdSubexercise.getTotalSubexerciseCredits(), closeTo(60, 0));
        assertThat(createdSubexercise.getNumber(), is("c"));


        // assert that subexercise was added to the assignment in the repository
        assertThat(assignmentRepository.getReferenceById(assignmentId).getExercises().getFirst().getSubexercises().stream().map(SubexerciseEntity::getId).toList(), hasItem(subexerciseId));

        final AssignmentEntity assignmentFromRepo = assignmentRepository.getReferenceById(assignmentId);
        final List<ExerciseEntity> exercisesFromRepo = assignmentFromRepo.getExercises();
        final ExerciseEntity exerciseEntityFromRepo = exercisesFromRepo.getFirst();
        final SubexerciseEntity createdSubexerciseFromRepo = exerciseEntityFromRepo
                .getSubexercises().stream()
                .filter(subexercise -> subexercise.getId().equals(subexerciseId))
                .findFirst().get();


        assertThat(assignmentFromRepo.getTotalCredits(), is(assignmentCredits + 60));
        assertThat(exerciseEntityFromRepo.getTotalExerciseCredits(), is(assignmentCredits + 60)); // same credits because there is only one exercise in the assignment

        assertThat(createdSubexerciseFromRepo.getItemId(), is(subexerciseId));
        assertThat(createdSubexerciseFromRepo.getTotalSubexerciseCredits(), closeTo(60, 0));
        assertThat(createdSubexerciseFromRepo.getNumber(), is("c"));

        assertThat(exerciseEntityFromRepo.getSubexercises(), hasSize(3));
        assertThat(exerciseEntityFromRepo.getSubexercises(), hasItem(
                SubexerciseEntity.builder().itemId(subexerciseId).parentExercise(exerciseEntityFromRepo).totalSubexerciseCredits(60).number("c").build()
        ));
    }

    @Test
    void testCreateInvalidSubexerciseWrongExerciseId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID subexerciseId = UUID.randomUUID();
        final UUID assignmentId = assignmentEntity.getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        createSubexercise(input: {
                            itemId: $subexerciseId,
                            parentExerciseId: $exerciseId,
                            totalSubexerciseCredits: 60,
                            number: "c"
                        }) {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
                    }
                }
                """;


        final UUID wrongId = UUID.randomUUID();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", assignmentId)
                .variable("subexerciseId", subexerciseId)
                .variable("exerciseId", wrongId)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Exercise with itemId %s not found", wrongId)));

    }

    @Test
    void testCreateInvalidSubexerciseWrongAssignmentId(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID subexerciseId = UUID.randomUUID();
        final UUID exerciseId = assignmentEntity.getExercises().getFirst().getId();

        final String query = """
                mutation ($assignmentId: UUID!, $exerciseId: UUID!, $subexerciseId: UUID!) {
                    mutateAssignment(assessmentId: $assignmentId) {
                        createSubexercise(input: {
                            itemId: $subexerciseId,
                            parentExerciseId: $exerciseId,
                            totalSubexerciseCredits: 60,
                            number: "c"
                        }) {
                            itemId
                            totalSubexerciseCredits
                            number
                        }
                    }
                }
                """;


        final UUID wrongId = UUID.randomUUID();

        // Execute the mutation and expect exception
        tester.document(query)
                .variable("assignmentId", wrongId)
                .variable("subexerciseId", subexerciseId)
                .variable("exerciseId", exerciseId)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains(String.format("Assignment with assessmentId %s not found", wrongId)));

    }


}
