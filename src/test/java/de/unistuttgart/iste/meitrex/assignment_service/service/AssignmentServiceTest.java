package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.CodeAssignmentMetadataEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCourseRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentChangeEvent;
import de.unistuttgart.iste.meitrex.common.event.CrudOperation;
import de.unistuttgart.iste.meitrex.common.exception.IncompleteEventMessageException;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentType;
import de.unistuttgart.iste.meitrex.generated.dto.Course;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import de.unistuttgart.iste.meitrex.generated.dto.UpdateAssignmentInput;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentServiceTest {

    private final AssignmentRepository assignmentRepository = Mockito.mock(AssignmentRepository.class);
    private final GradingRepository gradingRepository = Mockito.mock(GradingRepository.class);
    private final AssignmentMapper assignmentMapper = new AssignmentMapper(new ModelMapper());
    private final AssignmentValidator assignmentValidator = new AssignmentValidator();
    private final TopicPublisher topicPublisher = Mockito.mock(TopicPublisher.class);
    private final CourseServiceClient courseServiceClient = Mockito.mock(CourseServiceClient.class);
    private final ContentServiceClient contentServiceClient = Mockito.mock(ContentServiceClient.class);
    private final CodeAssessmentProvider codeAssessmentProvider = Mockito.mock(CodeAssessmentProvider.class);
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository = Mockito.mock(ExternalCodeAssignmentRepository.class);
    private final ExternalCourseRepository externalCourseRepository = Mockito.mock(ExternalCourseRepository.class);

    private final AssignmentService assignmentService = new AssignmentService(assignmentRepository, assignmentMapper, assignmentValidator, topicPublisher, courseServiceClient, contentServiceClient, codeAssessmentProvider, externalCodeAssignmentRepository, gradingRepository, externalCourseRepository);

    private UUID courseId = UUID.randomUUID();
    private String courseTitle;
    private Course mockCourse;

    @InjectCurrentUserHeader
    private LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);


    @BeforeEach
    void setup() throws CourseServiceConnectionException {
        courseTitle = "Software Engineering";
        mockCourse = Course.builder()
                .setId(courseId)
                .setTitle(courseTitle)
                .setDescription("mock")
                .setStartDate(OffsetDateTime.now())
                .setEndDate(OffsetDateTime.now().plusDays(30))
                .setPublished(true)
                .setMemberships(List.of())
                .build();

        when(courseServiceClient.queryCourseById(courseId)).thenReturn(mockCourse);
    }

    @Test
    void deleteAssignmentIfContentIsDeletedValidTest() {
        // init
        final UUID assessmentId = UUID.randomUUID();

        final AssignmentEntity assignmentEntity = AssignmentEntity.builder()
                .assessmentId(assessmentId)
                .exercises(new ArrayList<>())
                .build();

        final ContentChangeEvent contentChangeEvent = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(CrudOperation.DELETE)
                .build();

        //mock repository
        when(assignmentRepository.findAllById(contentChangeEvent.getContentIds())).thenReturn(List.of(assignmentEntity));

        // invoke method under test
        assertDoesNotThrow(() -> assignmentService.deleteAssignmentIfContentIsDeleted(contentChangeEvent));
        verify(assignmentRepository, times(1)).deleteAllById(any());
    }

    @Test
    void deleteAssignmentIfContentIsDeletedAlsoDeletesGradingsTest() {
        UUID assessmentId = UUID.randomUUID();
        ContentChangeEvent contentChangeEvent = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(CrudOperation.DELETE)
                .build();

        AssignmentEntity assignment = AssignmentEntity.builder()
                .assessmentId(assessmentId)
                .exercises(new ArrayList<>())
                .build();

        GradingEntity gradingEntity = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(UUID.randomUUID(), assessmentId))
                .build();

        when(assignmentRepository.findAllById(contentChangeEvent.getContentIds()))
                .thenReturn(List.of(assignment));
        when(gradingRepository.findAllByPrimaryKey_AssessmentIdIn(List.of(assessmentId)))
                .thenReturn(List.of(gradingEntity));

        assertDoesNotThrow(() -> assignmentService.deleteAssignmentIfContentIsDeleted(contentChangeEvent));

        verify(gradingRepository, times(1)).findAllByPrimaryKey_AssessmentIdIn(List.of(assessmentId));
        verify(gradingRepository, times(1)).deleteAll(List.of(gradingEntity));
        verify(assignmentRepository, times(1)).deleteAllById(List.of(assessmentId));
    }

    @Test
    void deleteAssignmentIfContentIsDeletedWithNoAssignmentsTest() {
        //init
        UUID assessmentId = UUID.randomUUID();

        ContentChangeEvent contentChangeEvent = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(CrudOperation.DELETE)
                .build();

        //mock repository
        when(assignmentRepository.findAllById(contentChangeEvent.getContentIds())).thenReturn(new ArrayList<AssignmentEntity>());

        // invoke method under test
        assertDoesNotThrow(() -> assignmentService.deleteAssignmentIfContentIsDeleted(contentChangeEvent));
        verify(assignmentRepository, times(1)).deleteAllById(any());
    }

    @Test
    void deleteAssignmentIfContentIsDeletedInvalidEventsTest() {
        //init
        UUID assessmentId = UUID.randomUUID();

        ContentChangeEvent emptyListDto = ContentChangeEvent.builder()
                .contentIds(List.of())
                .operation(CrudOperation.DELETE)
                .build();

        ContentChangeEvent nullListDto = ContentChangeEvent.builder()
                .contentIds(null)
                .operation(CrudOperation.DELETE)
                .build();

        ContentChangeEvent nullOperationDto = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(null)
                .build();

        ContentChangeEvent creationEvent = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(CrudOperation.CREATE)
                .build();

        ContentChangeEvent updateEvent = ContentChangeEvent.builder()
                .contentIds(List.of(assessmentId))
                .operation(CrudOperation.UPDATE)
                .build();

        List<ContentChangeEvent> events = List.of(emptyListDto, creationEvent, updateEvent);
        List<ContentChangeEvent> errorEvents = List.of(nullListDto, nullOperationDto);

        for (ContentChangeEvent event : events) {
            // invoke method under test
            assertDoesNotThrow(() -> assignmentService.deleteAssignmentIfContentIsDeleted(event));
            verify(assignmentRepository, never()).findAllById(any());
            verify(assignmentRepository, never()).deleteAllById(any());
        }

        for (ContentChangeEvent errorEvent : errorEvents) {
            // invoke method under test
            assertThrows(IncompleteEventMessageException.class, () -> assignmentService.deleteAssignmentIfContentIsDeleted(errorEvent));
            verify(assignmentRepository, never()).findAllById(any());
            verify(assignmentRepository, never()).deleteAllById(any());
        }
    }

    @Test
    void updateCodeAssignment_UpdatesRequiredPercentage() {
        UUID assessmentId = UUID.randomUUID();

        AssignmentEntity assignmentEntity = AssignmentEntity.builder()
                .assessmentId(assessmentId)
                .assignmentType(AssignmentType.CODE_ASSIGNMENT)
                .courseId(courseId)
                .requiredPercentage(0.5)
                .build();

        CodeAssignmentMetadataEntity metadata = CodeAssignmentMetadataEntity.builder()
                .assignment(assignmentEntity)
                .assignmentLink("link")
                .invitationLink("link")
                .build();

        assignmentEntity.setCodeAssignmentMetadata(metadata);

        double newPercentage = 0.8;
        UpdateAssignmentInput input = UpdateAssignmentInput.builder()
                .setRequiredPercentage(newPercentage)
                .build();

        when(assignmentRepository.findById(assessmentId)).thenReturn(Optional.of(assignmentEntity));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0)); // return saved entity

        var result = assignmentService.updateAssignment(assessmentId, input, loggedInUser);

        assertNotNull(result);
        assertEquals(newPercentage, result.getRequiredPercentage());
        verify(assignmentRepository).save(assignmentEntity);
    }

    @Test
    void updateAssignment_ThrowsIfAssignmentNotFound() {
        UUID assessmentId = UUID.randomUUID();
        UpdateAssignmentInput input = UpdateAssignmentInput.builder()
                .setRequiredPercentage(0.75)
                .build();

        when(assignmentRepository.findById(assessmentId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                assignmentService.updateAssignment(assessmentId, input, loggedInUser));
    }

    @Test
    void getExternalCodeAssignmentsReturnsNamesIfAccessible() throws CourseServiceConnectionException {
        List<String> mockNames = List.of("Assignment A", "Assignment B");

        when(courseServiceClient.queryCourseById(courseId)).thenReturn(mockCourse);
        when(externalCodeAssignmentRepository.findAssignmentNamesByCourseTitle(courseTitle)).thenReturn(mockNames);

        List<String> result = assignmentService.getExternalCodeAssignments(courseId, loggedInUser);

        assertEquals(mockNames, result);
        verify(courseServiceClient).queryCourseById(courseId);
        verify(externalCodeAssignmentRepository).findAssignmentNamesByCourseTitle(courseTitle);
    }

    @Test
    void fetchesAndSavesCourse() throws Exception {
        String organizationName = "TestOrg";
        String url = "https://classroom.github.com/mycourse";
        ExternalCourse external = new ExternalCourse(courseTitle, url, organizationName);
        when(codeAssessmentProvider.getExternalCourse(courseTitle, loggedInUser)).thenReturn(external);

        ExternalCourse course = assignmentService.getExternalCourse(courseId, loggedInUser);

        assertNotNull(course);
        assertEquals(courseTitle, course.getCourseTitle());
        assertEquals(organizationName, course.getOrganizationName());
        assertEquals(url, course.getUrl());
    }

    @Test
    void returnsNullIfUserNotAdmin() {
        ExternalCourse course = assignmentService.getExternalCourse(courseId, loggedInUser);
        assertNull(course);
    }

    @Test
    void returnsNullIfExternalProviderFails() throws Exception {
        String courseTitle = "Broken Course";
        when(codeAssessmentProvider.getExternalCourse(courseTitle, loggedInUser))
                .thenThrow(new ExternalPlatformConnectionException("Timeout"));

        ExternalCourse result = assignmentService.getExternalCourse(courseId, loggedInUser);

        assertNull(result);
    }

}
