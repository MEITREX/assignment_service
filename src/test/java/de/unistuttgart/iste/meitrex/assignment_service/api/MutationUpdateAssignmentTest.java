package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.test_config.MockedTopicPublisherConfig;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.AssessmentContentMutatedEvent;
import de.unistuttgart.iste.meitrex.common.event.AssessmentType;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@GraphQlApiTest
@ContextConfiguration(classes = {MockedTopicPublisherConfig.class})
class MutationUpdateAssignmentTest {

    @Autowired
    private AssignmentRepository assignmentRepository;
    private final UUID courseId = UUID.randomUUID();

    @Autowired
    private TestUtils testUtils;

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser =
            userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TopicPublisher topicPublisher;

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

        ArgumentCaptor<AssessmentContentMutatedEvent> eventCaptor = ArgumentCaptor.forClass(AssessmentContentMutatedEvent.class);
        verify(topicPublisher, atLeastOnce()).notifyAssessmentContentMutated(eventCaptor.capture());

        List<AssessmentContentMutatedEvent> allEvents = eventCaptor.getAllValues();
        AssessmentContentMutatedEvent event = allEvents.stream()
                .filter(e -> e.getAssessmentId().equals(assignmentId) &&
                        e.getTaskInformationList().getFirst().getTextualRepresentation().contains("0.8"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Updated event with new percentage not found"));

        assertThat(event.getCourseId(), is(courseId));
        assertThat(event.getAssessmentId(), is(assignmentId));
        assertThat(event.getAssessmentType(), is(AssessmentType.ASSIGNMENT));
        assertThat(event.getTaskInformationList(), hasSize(1));

        String taskText = event.getTaskInformationList().getFirst().getTextualRepresentation();
        assertThat(taskText, containsString("Required Percentage: " + newPercentage));
    }
}
