package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.event.Response;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.testutil.MockTestPublisherConfiguration;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentCompletedFeedback;
import de.unistuttgart.iste.meitrex.generated.dto.ExerciseCompletedInput;
import de.unistuttgart.iste.meitrex.generated.dto.LogAssignmentCompletedInput;
import de.unistuttgart.iste.meitrex.generated.dto.SubexerciseCompletedInput;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@GraphQlApiTest
@ContextConfiguration(classes = MockTestPublisherConfiguration.class)
class MutationLogAssignmentCompletedTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @Autowired
    private TopicPublisher mockTopicPublisher;

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;


    /**
     * Given a successfully completed assignment
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then the dapr topic publisher is called and the correct feedback is returned
     */
    @Test
    @Transactional
    @Commit
    void testValidLogAssignmentCompletedSuccessful(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(20)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(15)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(35)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(35)
                .setCompletedExercises(completedExercisesInput)
                .build();

        final AssignmentCompletedFeedback actualFeedback = tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .path("logAssignmentCompleted").entity(AssignmentCompletedFeedback.class)
                .get();

        final AssignmentCompletedFeedback expectedFeedback = AssignmentCompletedFeedback.builder()
                .setSuccess(true)
                .setCorrectness(35.0/50.0)
                .build();

        assertThat(actualFeedback, is(expectedFeedback));


        final Response responseExercise1 = new Response(exerciseEntity.getItemId(), (35.0f/50.0f));
        final Response responseSubexercise1 = new Response(subexerciseEntity1.getItemId(), (20.0f/30.0f));
        final Response responseSubexercise2 = new Response(subexerciseEntity2.getItemId(), (15.0f/20.0f));

        final List<Response> responses = List.of(responseExercise1, responseSubexercise1, responseSubexercise2);

        final ContentProgressedEvent expectedContentProgressedEvent = ContentProgressedEvent.builder()
                .userId(loggedInUser.getId())
                .contentId(assignmentId)
                .timeToComplete(null)
                .correctness(35.0/50.0)
                .hintsUsed(0)
                .success(true)
                .responses(responses)
                .build();

        verify(mockTopicPublisher, times(1))
                .notifyUserWorkedOnContent(expectedContentProgressedEvent);


    }

    /**
     * Given a not-successfully completed assignment
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then the dapr topic publisher is called and the correct feedback is returned
     */
    @Test
    void testValidLogAssignmentCompletedNotSuccessful(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(10)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(5)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(15)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(15)
                .setCompletedExercises(completedExercisesInput)
                .build();

        final AssignmentCompletedFeedback actualFeedback = tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .path("logAssignmentCompleted").entity(AssignmentCompletedFeedback.class)
                .get();

        final AssignmentCompletedFeedback expectedFeedback = AssignmentCompletedFeedback.builder()
                .setSuccess(false)
                .setCorrectness(15.0/50.0)
                .build();

        assertThat(actualFeedback, is(expectedFeedback));


        final Response responseExercise1 = new Response(exerciseEntity.getItemId(), (15.0f/50.0f));
        final Response responseSubexercise1 = new Response(subexerciseEntity1.getItemId(), (10.0f/30.0f));
        final Response responseSubexercise2 = new Response(subexerciseEntity2.getItemId(), (05.0f/20.0f));

        final List<Response> responses = List.of(responseExercise1, responseSubexercise1, responseSubexercise2);

        final ContentProgressedEvent expectedContentProgressedEvent = ContentProgressedEvent.builder()
                .userId(loggedInUser.getId())
                .contentId(assignmentId)
                .timeToComplete(null)
                .correctness(15.0/50.0)
                .hintsUsed(0)
                .success(false)
                .responses(responses)
                .build();

        verify(mockTopicPublisher, times(1))
                .notifyUserWorkedOnContent(expectedContentProgressedEvent);


    }

    /**
     * Given a completed assignment with a required percentage of 90%
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then the dapr topic publisher is called and the correct feedback is returned
     */
    @Test
    @Transactional
    @Commit
    void testValidLogAssignmentCompletedNewPercentage(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        assignmentEntity.setRequiredPercentage(0.9);
        assignmentRepository.save(assignmentEntity);

        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(20)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(15)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(35)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(35)
                .setCompletedExercises(completedExercisesInput)
                .build();

        final AssignmentCompletedFeedback actualFeedback = tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .path("logAssignmentCompleted").entity(AssignmentCompletedFeedback.class)
                .get();

        final AssignmentCompletedFeedback expectedFeedback = AssignmentCompletedFeedback.builder()
                .setSuccess(false)
                .setCorrectness(35.0/50.0)
                .build();

        assertThat(actualFeedback, is(expectedFeedback));


        final Response responseExercise1 = new Response(exerciseEntity.getItemId(), (35.0f/50.0f));
        final Response responseSubexercise1 = new Response(subexerciseEntity1.getItemId(), (20.0f/30.0f));
        final Response responseSubexercise2 = new Response(subexerciseEntity2.getItemId(), (15.0f/20.0f));

        final List<Response> responses = List.of(responseExercise1, responseSubexercise1, responseSubexercise2);

        final ContentProgressedEvent expectedContentProgressedEvent = ContentProgressedEvent.builder()
                .userId(loggedInUser.getId())
                .contentId(assignmentId)
                .timeToComplete(null)
                .correctness(35.0/50.0)
                .hintsUsed(0)
                .success(false)
                .responses(responses)
                .build();

        verify(mockTopicPublisher, times(1))
                .notifyUserWorkedOnContent(expectedContentProgressedEvent);


    }


    /**
     * Given a completed assignment with 0 total credits
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then the dapr topic publisher is called and the correct feedback is returned
     */
    @Test
    void testValidLogAssignmentCompletedZeroCredits(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        assignmentEntity.setTotalCredits(0.0);
        assignmentEntity.getExercises().forEach(exercise -> exercise.setTotalExerciseCredits(0));
        assignmentEntity.getExercises().forEach(exercise -> exercise.getSubexercises().forEach(subexercise -> subexercise.setTotalSubexerciseCredits(0)));
        assignmentRepository.save(assignmentEntity);

        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(0.0f)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(0.0f)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(0.0f)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(0.0f)
                .setCompletedExercises(completedExercisesInput)
                .build();

        final AssignmentCompletedFeedback actualFeedback = tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .path("logAssignmentCompleted").entity(AssignmentCompletedFeedback.class)
                .get();

        final AssignmentCompletedFeedback expectedFeedback = AssignmentCompletedFeedback.builder()
                .setSuccess(true)
                .setCorrectness(1.0f)
                .build();

        assertThat(actualFeedback, is(expectedFeedback));


        final Response responseExercise1 = new Response(exerciseEntity.getItemId(), (1));
        final Response responseSubexercise1 = new Response(subexerciseEntity1.getItemId(), (1));
        final Response responseSubexercise2 = new Response(subexerciseEntity2.getItemId(), (1));

        final List<Response> responses = List.of(responseExercise1, responseSubexercise1, responseSubexercise2);

        final ContentProgressedEvent expectedContentProgressedEvent = ContentProgressedEvent.builder()
                .userId(loggedInUser.getId())
                .contentId(assignmentId)
                .timeToComplete(null)
                .correctness(1.0f)
                .hintsUsed(0)
                .success(true)
                .responses(responses)
                .build();

        verify(mockTopicPublisher, times(1))
                .notifyUserWorkedOnContent(expectedContentProgressedEvent);


    }

    /**
     * Given a successfully completed assignment, where the sum of achieved exercise credits does not add up to achieved assignment credits,
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then a validation exception is thrown
     */
    @Test
    void testInvalidLogAssignmentCompletedIncorrectExerciseSum(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(10)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(5)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(15)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(20)
                .setCompletedExercises(completedExercisesInput)
                .build();

        tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains("Achieved exercise credits do not sum up to achieved assignment credits"));

    }

    /**
     * Given a successfully completed assignment, where the sum of achieved subexercise credits does not add up to achieved exercise credits,
     * When the "logAssignmentCompleted" mutation is called with the assignment's assessment id
     * Then a validation exception is thrown
     */
    @Test
    void testInvalidLogAssignmentCompletedIncorrectSubexerciseSum(final GraphQlTester tester) {
        final AssignmentEntity assignmentEntity = testUtils.populateAssignmentRepository(assignmentRepository, courseId);
        final UUID assignmentId = assignmentEntity.getId();
        final ExerciseEntity exerciseEntity = assignmentEntity.getExercises().getFirst();
        final SubexerciseEntity subexerciseEntity1 = exerciseEntity.getSubexercises().get(0);
        final SubexerciseEntity subexerciseEntity2 = exerciseEntity.getSubexercises().get(1);

        final String query = """
                mutation($input: LogAssignmentCompletedInput!) {
                    logAssignmentCompleted(input: $input) {
                        success
                        correctness
                    }
                }
                """;


        final SubexerciseCompletedInput subexerciseCompletedInput1 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity1.getItemId())
                .setAchievedCredits(10)
                .build();
        final SubexerciseCompletedInput subexerciseCompletedInput2 = SubexerciseCompletedInput.builder()
                .setItemId(subexerciseEntity2.getItemId())
                .setAchievedCredits(5)
                .build();
        final List<SubexerciseCompletedInput> completedSubexercisesInput = List.of(subexerciseCompletedInput1, subexerciseCompletedInput2);

        final ExerciseCompletedInput exerciseCompletedInput1 = ExerciseCompletedInput.builder()
                .setItemId(exerciseEntity.getItemId())
                .setAchievedCredits(20)
                .setCompletedSubexercises(completedSubexercisesInput)
                .build();
        final List<ExerciseCompletedInput> completedExercisesInput = List.of(exerciseCompletedInput1);

        final LogAssignmentCompletedInput assignmentCompletedInput = LogAssignmentCompletedInput.builder()
                .setAssessmentId(assignmentId)
                .setAchievedCredits(20)
                .setCompletedExercises(completedExercisesInput)
                .build();

        tester.document(query)
                .variable("input", assignmentCompletedInput)
                .execute()
                .errors()
                .expect(responseError -> responseError.getMessage() != null && responseError.getMessage().contains("Achieved subexercise credits do not sum up to achieved exercise credits"));

    }


}
