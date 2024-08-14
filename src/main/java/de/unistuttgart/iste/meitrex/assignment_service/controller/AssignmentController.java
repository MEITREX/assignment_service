package de.unistuttgart.iste.meitrex.assignment_service.controller;

import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.assignment_service.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @QueryMapping
    public List<Assignment> findAssignmentsByAssessmentIds(List<UUID> assessmentIds) {
        return assignmentService.findAssignmentsByAssessmentIds(assessmentIds);
    }

}
