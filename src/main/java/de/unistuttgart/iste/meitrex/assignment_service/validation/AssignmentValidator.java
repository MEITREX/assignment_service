package de.unistuttgart.iste.meitrex.assignment_service.validation;

import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.generated.dto.CreateAssignmentInput;
import org.springframework.stereotype.Component;

@Component
public class AssignmentValidator {

    public void validateAssignment(Assignment assignment) {
        // add validation logic here
    }

    public void validateCreateAssignmentInput(final CreateAssignmentInput createAssignmentInput) {
        // no extra validation needed
    }
}
