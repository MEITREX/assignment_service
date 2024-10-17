package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@GraphQlApiTest
public class MutationCreateAssignmentTest {

    @Test
    void testCreateAssignment(final GraphQlTester tester) {
        final UUID assessmentId = UUID.randomUUID();
        final UUID courseId = UUID.randomUUID();
        final UUID itemId1 = UUID.randomUUID();
        final UUID itemId2 = UUID.randomUUID();
        final UUID itemId3 = UUID.randomUUID();
        final UUID itemId4 = UUID.randomUUID();
        final UUID itemId5 = UUID.randomUUID();

        final String query = """
                mutation ($courseId: UUID!, $assessmentId: UUID!, $itemId1: UUID!, $itemId2: UUID!, $itemId3: UUID!, $itemId4: UUID!, $itemId5: UUID!){
                    _internal_noauth_createAssignment(courseId: $courseId, assessmentId: $assessmentId, input: {
                        totalCredits: 100,
                        exercises: [
                        {
                            itemId: $itemId1,
                            totalExerciseCredits: 25,
                            subexercises: [
                            {
                                itemId: $itemId2,
                                parentExerciseId: $itemId1,
                                totalSubexerciseCredits: 20,
                                number: "one a"
                            },
                            {
                                itemId: $itemId3,
                                parentExerciseId: $itemId1,
                                totalSubexerciseCredits: 5,
                                number: "one b"
                            }
                            ],
                            number: "one"
                        },
                        {
                            itemId: $itemId4,
                            totalExerciseCredits: 75,
                            subexercises: [
                            {
                                itemId: $itemId5,
                                parentExerciseId: $itemId4,
                                totalSubexerciseCredits: 75
                                number: "two a"
                            }
                            ],
                            number: "two"
                        }
                        ],
                        assignmentType: EXERCISE_SHEET,
                        date: "2021-01-01T00:00:00.000Z",
                        description: "exercise sheet 1",
                        requiredPercentage: 0.2,
                        externalId: "123456789123456789"
                        }
                    )
                    {
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
                        externalId
                    }
                }
                """;

        final Assignment createdAssignment = tester.document(query)
                .variable("assessmentId", assessmentId)
                .variable("courseId", courseId)
                .variable("itemId1", itemId1)
                .variable("itemId2", itemId2)
                .variable("itemId3", itemId3)
                .variable("itemId4", itemId4)
                .variable("itemId5", itemId5)
                .execute()
                .path("_internal_noauth_createAssignment").entity(Assignment.class).get();

        // check that created Assignment is correct

        assertThat(createdAssignment.getAssessmentId(), is(assessmentId));
        assertThat(createdAssignment.getCourseId(), is(courseId));
        assertThat(createdAssignment.getTotalCredits(), closeTo(100, 0));
        assertThat(createdAssignment.getAssignmentType(), is(AssignmentType.EXERCISE_SHEET));
        assertThat(createdAssignment.getDate(), is(LocalDate.of(2021, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC)));
        assertThat(createdAssignment.getDescription(), is("exercise sheet 1"));
        assertThat(createdAssignment.getRequiredPercentage(), is(0.2));
        assertThat(createdAssignment.getExternalId(), is("123456789123456789"));

        final List<Exercise> exercises = createdAssignment.getExercises();
        assertThat(exercises, hasSize(2));

        final Exercise exercise1 = exercises.get(0);
        assertThat(exercise1.getItemId(), is(itemId1));
        assertThat(exercise1.getTotalExerciseCredits(), closeTo(25, 0));
        assertThat(exercise1.getNumber(), is("one"));

        final List<Subexercise> subexercises1 = exercise1.getSubexercises();
        assertThat(subexercises1, hasSize(2));

        final Subexercise ex1subex1 = subexercises1.get(0);
        assertThat(ex1subex1.getItemId(), is(itemId2));
        assertThat(ex1subex1.getTotalSubexerciseCredits(), closeTo(20, 0));
        assertThat(ex1subex1.getNumber(), is("one a"));

        final Subexercise ex1subex2 = subexercises1.get(1);
        assertThat(ex1subex2.getItemId(), is(itemId3));
        assertThat(ex1subex2.getTotalSubexerciseCredits(), closeTo(5, 0));
        assertThat(ex1subex2.getNumber(), is("one b"));

        final Exercise exercise2 = exercises.get(1);
        assertThat(exercise2.getItemId(), is(itemId4));
        assertThat(exercise2.getTotalExerciseCredits(), closeTo(75, 0));
        assertThat(exercise2.getNumber(), is("two"));

        final List<Subexercise> subexercises2 = exercise2.getSubexercises();
        assertThat(subexercises2, hasSize(1));

        final Subexercise ex2subex1 = subexercises2.get(0);
        assertThat(ex2subex1.getItemId(), is(itemId5));
        assertThat(ex2subex1.getTotalSubexerciseCredits(), closeTo(75, 0));
        assertThat(ex2subex1.getNumber(), is("two a"));

    }

}
