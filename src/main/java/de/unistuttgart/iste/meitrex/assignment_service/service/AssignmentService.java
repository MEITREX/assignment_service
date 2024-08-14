package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentMapper assignmentMapper;


    /**
     * Returns all assignments
     *
     * @return list of assignments
     */
    public List<Assignment> getAllAssignments() {
        List<AssignmentEntity> assignmentEntities = assignmentRepository.findAll();
        return assignmentEntities.stream().map(assignmentMapper::assignmentEntityToDto).toList();
    }

    /**
     * Returns all assignments that are linked to the given assessment ids
     *
     * @param ids list of assessment ids
     * @return list of assignments, an element is null if the corresponding assessment id was not found
     */
    public List<Assignment> findAssignmentsByAssessmentIds(final List<UUID> ids) {
        return assignmentRepository.findAllByIdPreservingOrder(ids).stream()
                .map(assignmentMapper::assignmentEntityToDto)
                .toList();
    }
}
