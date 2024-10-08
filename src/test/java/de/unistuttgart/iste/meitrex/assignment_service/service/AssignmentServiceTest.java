package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentChangeEvent;
import de.unistuttgart.iste.meitrex.common.event.CrudOperation;
import de.unistuttgart.iste.meitrex.common.exception.IncompleteEventMessageException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AssignmentServiceTest {

    private final AssignmentRepository assignmentRepository = Mockito.mock(AssignmentRepository.class);
    private final AssignmentMapper assignmentMapper = new AssignmentMapper(new ModelMapper());
    private final AssignmentValidator assignmentValidator = new AssignmentValidator();
    private final TopicPublisher topicPublisher = Mockito.mock(TopicPublisher.class);

    private final AssignmentService assignmentService = new AssignmentService(assignmentRepository, assignmentMapper, assignmentValidator, topicPublisher);

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
}
