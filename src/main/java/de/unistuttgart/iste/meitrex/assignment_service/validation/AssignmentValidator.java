package de.unistuttgart.iste.meitrex.assignment_service.validation;

import de.unistuttgart.iste.meitrex.generated.dto.*;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssignmentValidator {
    private final double EPSILON = 0.01;

    public void validateCreateAssignmentInput(final CreateAssignmentInput createAssignmentInput) {
        final List<CreateExerciseInput> exercises = createAssignmentInput.getExercises();

        // checks if all exercise credits sum up to assignment credits
        double exerciseCreditSum = exercises.stream().mapToDouble(CreateExerciseInput::getTotalExerciseCredits).sum();
        if (Math.abs(createAssignmentInput.getTotalCredits() - exerciseCreditSum) > EPSILON)
            throw new ValidationException("Exercise credits do not sum up to total assignment credits.");

        // checks all underlying exercise and subexercise inputs
        for (final CreateExerciseInput exercise : exercises) {
            this.validateCreateExerciseInput(exercise);
        }
    }

    public void validateCreateExerciseInput(final CreateExerciseInput createExerciseInput) {
        final List<CreateSubexerciseInput> subexercises = createExerciseInput.getSubexercises();

        // checks if all subexercise credits sum up to exercise credits
        double subexerciseCreditSum = subexercises.stream().mapToDouble(CreateSubexerciseInput::getTotalSubexerciseCredits).sum();
        if (Math.abs(createExerciseInput.getTotalExerciseCredits() - subexerciseCreditSum) > EPSILON)
            throw new ValidationException("Subexercise credits do not sum up to total exercise credits.");

        // checks all underlying subexercise inputs
        for (final CreateSubexerciseInput subexercise : subexercises) {
            this.validateCreateSubexerciseInput(subexercise);
        }
    }

    public void validateUpdateExerciseInput(final UpdateExerciseInput updateExerciseInput) {
        final List<CreateSubexerciseInput> subexercises = updateExerciseInput.getSubexercises();

        // checks if all subexercise credits sum up to exercise credits
        double subexerciseCreditSum = subexercises.stream().mapToDouble(CreateSubexerciseInput::getTotalSubexerciseCredits).sum();
        if (Math.abs(updateExerciseInput.getTotalExerciseCredits() - subexerciseCreditSum) > EPSILON)
            throw new ValidationException("Subexercise credits do not sum up to total exercise credits.");

    }

    public void validateCreateSubexerciseInput(final CreateSubexerciseInput createSubexerciseInput) {
        // no extra validation needed
    }

    public void validateUpdateSubexerciseInput(final UpdateSubexerciseInput updateSubexerciseInput) {
        // no extra validation needed
    }

    public void validateLogAssignmentCompletedInput(final LogAssignmentCompletedInput input) {
        final double assignmentCredits = input.getAchievedCredits();
        double exerciseSum = 0;

        for (final ExerciseCompletedInput exerciseCompletedInput : input.getCompletedExercises()) {
            double subexerciseSum = 0;
            double exerciseCredits = exerciseCompletedInput.getAchievedCredits();
            exerciseSum += exerciseCredits;
            for (final SubexerciseCompletedInput subexerciseCompletedInput : exerciseCompletedInput.getCompletedSubexercises()) {
                subexerciseSum += subexerciseCompletedInput.getAchievedCredits();
            }
            if (Math.abs(exerciseCredits - subexerciseSum) > EPSILON) {
                throw new ValidationException("Achieved subexercise credits do not sum up to achieved exercise credits.");
            }
        }
        if (Math.abs(assignmentCredits - exerciseSum) > EPSILON) {
            throw new ValidationException("Achieved exercise credits do not sum up to achieved assignment credits");
        }
    }
}
