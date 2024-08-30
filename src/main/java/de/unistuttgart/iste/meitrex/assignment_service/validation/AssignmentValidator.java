package de.unistuttgart.iste.meitrex.assignment_service.validation;

import de.unistuttgart.iste.meitrex.generated.dto.*;
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

    public void validateCreateSubexerciseInput(final CreateSubexerciseInput createSubexerciseInput) {

    }

    public void validateUpdateSubexerciseInput(final UpdateSubexerciseInput updateSubexerciseInput) {

    }
}
