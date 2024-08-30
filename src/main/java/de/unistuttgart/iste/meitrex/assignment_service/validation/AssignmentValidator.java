package de.unistuttgart.iste.meitrex.assignment_service.validation;

import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.generated.dto.CreateAssignmentInput;
import de.unistuttgart.iste.meitrex.generated.dto.CreateExerciseInput;
import de.unistuttgart.iste.meitrex.generated.dto.UpdateExerciseInput;
import org.springframework.stereotype.Component;

@Component
public class AssignmentValidator {

    public void validateCreateAssignmentInput(final CreateAssignmentInput createAssignmentInput) {
        // no extra validation needed
    }

    public void validateCreateExerciseInput(final CreateExerciseInput createExerciseInput) {

    }

    public void validateUpdateExerciseInput(final UpdateExerciseInput updateExerciseInput) {

    }
}
