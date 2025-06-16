package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExternalCourseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCourseRepository;
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
import de.unistuttgart.iste.meitrex.generated.dto.Course;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AssignmentServiceTest {

    private final AssignmentRepository assignmentRepository = Mockito.mock(AssignmentRepository.class);
    private final AssignmentMapper assignmentMapper = new AssignmentMapper(new ModelMapper());
    private final AssignmentValidator assignmentValidator = new AssignmentValidator();
    private final TopicPublisher topicPublisher = Mockito.mock(TopicPublisher.class);
    private final CourseServiceClient courseServiceClient = Mockito.mock(CourseServiceClient.class);
    private final ContentServiceClient contentServiceClient = Mockito.mock(ContentServiceClient.class);
    private final CodeAssessmentProvider codeAssessmentProvider = Mockito.mock(CodeAssessmentProvider.class);
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository = Mockito.mock(ExternalCodeAssignmentRepository.class);
    private final ExternalCourseRepository externalCourseRepository = Mockito.mock(ExternalCourseRepository.class);

    private final AssignmentService assignmentService = new AssignmentService(assignmentRepository, assignmentMapper, assignmentValidator, topicPublisher, courseServiceClient, contentServiceClient, codeAssessmentProvider, externalCodeAssignmentRepository, externalCourseRepository);

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
    void returnsCourseFromDatabaseIfPresent() {
        when(externalCourseRepository.findById(courseTitle)).thenReturn(Optional.of(
                new ExternalCourseEntity(courseTitle, "https://github.com/mycourse")));

        ExternalCourse course = assignmentService.getExternalCourse(courseId, loggedInUser);

        assertNotNull(course);
        assertEquals(courseTitle, course.getCourseTitle());
        assertEquals("https://github.com/mycourse", course.getUrl());
    }

    @Test
    void fetchesAndSavesCourseIfMissing() throws Exception {
        when(externalCourseRepository.findById(courseTitle)).thenReturn(Optional.empty());

        ExternalCourse external = new ExternalCourse(courseTitle, "https://classroom.github.com/mycourse");
        when(codeAssessmentProvider.getExternalCourse(courseTitle, loggedInUser)).thenReturn(external);
        when(externalCourseRepository.save(any())).thenReturn(new ExternalCourseEntity(courseTitle, external.getUrl()));

        ExternalCourse course = assignmentService.getExternalCourse(courseId, loggedInUser);

        assertNotNull(course);
        assertEquals(courseTitle, course.getCourseTitle());
        assertEquals("https://classroom.github.com/mycourse", course.getUrl());
    }

    @Test
    void returnsNullIfUserNotAdmin() {
        ExternalCourse course = assignmentService.getExternalCourse(courseId, loggedInUser);
        assertNull(course);
    }

    @Test
    void returnsNullIfExternalProviderFails() throws Exception {
        String courseTitle = "Broken Course";
        when(externalCourseRepository.findById(courseTitle)).thenReturn(Optional.empty());
        when(codeAssessmentProvider.getExternalCourse(courseTitle, loggedInUser))
                .thenThrow(new ExternalPlatformConnectionException("Timeout"));

        ExternalCourse result = assignmentService.getExternalCourse(courseId, loggedInUser);

        assertNull(result);
    }

}
