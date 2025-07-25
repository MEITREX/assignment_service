package de.unistuttgart.iste.meitrex.assignment_service.validation;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.ExerciseGradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssignmentValidator {
    private static final double EPSILON = 0.01;

    public void validateCreateAssignmentInput(final CreateAssignmentInput createAssignmentInput) {
        if (!hasExercises(createAssignmentInput.getAssignmentType())) {
            return;
        }

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

    /**
     * Validate that the amount of exercises and subexercises are the same in assignment and grading
     *
     * @param assignmentEntity the assignment entity
     * @param gradingEntity the grading entity that should fit the assignment entity
     */
    public void validateGradingEntityFitsAssignmentEntity(final AssignmentEntity assignmentEntity, final GradingEntity gradingEntity) {
        List<ExerciseEntity> assignmentExercises = assignmentEntity.getExercises();
        List<ExerciseGradingEntity> gradingExercises = gradingEntity.getExerciseGradings();

        if (assignmentExercises.size() != gradingExercises.size()) {
            throw new ValidationException("Not the same amount of exercises in grading and assignment!");
        }

        for (int i = 0; i < assignmentExercises.size(); i++) {
            if (assignmentExercises.get(i).getSubexercises().size() != gradingExercises.get(i).getSubexerciseGradings().size()) {
                throw new ValidationException("Not the same amount of subexercises in grading and assignment for exercise %d!".formatted(i));
            }
        }
    }

    // Since GitHub's API does not provide information about what should be done in the code assignment except for a Readme
    private boolean hasExercises(AssignmentType type) {
        return switch (type) {
            case EXERCISE_SHEET, PHYSICAL_TEST -> true;
            case CODE_ASSIGNMENT -> false;
        };
    }
}
