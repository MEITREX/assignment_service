package de.unistuttgart.iste.meitrex.assignment_service.controller;

import de.unistuttgart.iste.meitrex.assignment_service.service.GradingService;
import de.unistuttgart.iste.meitrex.common.exception.NoAccessToCourseException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import de.unistuttgart.iste.meitrex.assignment_service.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.user_handling.UserCourseAccessValidator.validateUserHasAccessToCourse;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final GradingService gradingService;

    /* Query Mappings */

    @QueryMapping
    public List<Assignment> findAssignmentsByAssessmentIds(@Argument List<UUID> assessmentIds, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.findAssignmentsByAssessmentIds(assessmentIds).stream()
                .map(assignment -> {
                    if (assignment == null) {
                        return null;
                    }
                    try {
                        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.STUDENT, assignment.getCourseId());
                        return assignment;
                    } catch (final NoAccessToCourseException ex) {
                        return null;
                    }
                })
                .toList();
    }

    @QueryMapping
    public List<String> getExternalCodeAssignments(@Argument final UUID courseId, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.getExternalCodeAssignments(courseId, currentUser);
    }

    @QueryMapping
    public ExternalCourse getExternalCourse(@Argument final UUID courseId, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.getExternalCourse(courseId, currentUser);
    }

    @QueryMapping
    public List<Grading> getGradingsForAssignment(@Argument final UUID assessmentId, @ContextValue final LoggedInUser currentUser) {
        return gradingService.getGradingsForAssignment(assessmentId,  currentUser);
    }

    @QueryMapping
    public List<ExternalAssignment> getExternalAssignments(@Argument final UUID courseId, @ContextValue final LoggedInUser currentUser) {
        return gradingService.getExternalAssignments(courseId, currentUser);
    }

    @QueryMapping
    public List<ManualMappingInstance> getManualMappingInstances(@Argument final UUID courseId, @ContextValue final LoggedInUser currentUser) {
        return gradingService.getManualMappingInstances(courseId, currentUser);
    }

    /* Mutation Mappings */

    @MutationMapping(name = "_internal_noauth_createAssignment")
    public Assignment createAssignment(@Argument final UUID courseId,
                                       @Argument final UUID assessmentId,
                                       @Argument final CreateAssignmentInput input,  @ContextValue final LoggedInUser currentUser) {
        return assignmentService.createAssignment(courseId, assessmentId, input, currentUser);
    }

    @MutationMapping
    public Assignment updateAssignment(@Argument UUID assessmentId,
                                       @Argument UpdateAssignmentInput input,
                                       @ContextValue LoggedInUser currentUser) {
        return assignmentService.updateAssignment(assessmentId, input, currentUser);
    }

    @MutationMapping
    public AssignmentMutation mutateAssignment(@Argument final UUID assessmentId, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.mutateAssignment(assessmentId, currentUser);
    }

    @MutationMapping
    public AssignmentCompletedFeedback logAssignmentCompleted(@Argument LogAssignmentCompletedInput input, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.logAssignmentCompleted(input, currentUser);
    }

    @MutationMapping
    public List<String> saveStudentMappings(@Argument final UUID courseId,
                                          @Argument final List<StudentMappingInput> studentMappingInputs,
                                          @ContextValue final LoggedInUser currentUser) {
        return gradingService.saveStudentMappings(courseId, studentMappingInputs, currentUser);
    }

    @MutationMapping
    public boolean syncAssignmentsForCourse(@Argument final UUID courseId, @ContextValue final LoggedInUser currentUser) {
        return assignmentService.syncAssignmentsForCourse(courseId, currentUser);
    }

    /* Schema Mappings */

    @SchemaMapping(typeName = "AssignmentMutation")
    public Exercise createExercise(@Argument(name = "input") final CreateExerciseInput input, final AssignmentMutation assignmentMutation) {
        return assignmentService.createExercise(assignmentMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "AssignmentMutation")
    public Exercise updateExercise(@Argument(name = "input") final UpdateExerciseInput input, final AssignmentMutation assignmentMutation) {
        return assignmentService.updateExercise(assignmentMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "AssignmentMutation")
    public UUID deleteExercise(@Argument(name = "itemId") final UUID itemId, final AssignmentMutation assignmentMutation) {
        return assignmentService.deleteExercise(assignmentMutation.getAssessmentId(), itemId);
    }

    @SchemaMapping(typeName = "AssignmentMutation")
    public Subexercise createSubexercise(@Argument(name = "input") final CreateSubexerciseInput input, final AssignmentMutation assignmentMutation) {
        return assignmentService.createSubexercise(assignmentMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "AssignmentMutation")
    public Subexercise updateSubexercise(@Argument(name = "input") final UpdateSubexerciseInput input, final AssignmentMutation assignmentMutation) {
        return assignmentService.updateSubexercise(assignmentMutation.getAssessmentId(), input);
    }

    @SchemaMapping(typeName = "AssignmentMutation")
    public UUID deleteSubexercise(@Argument(name = "itemId") final UUID itemId, final AssignmentMutation assignmentMutation) {
        return assignmentService.deleteSubexercise(assignmentMutation.getAssessmentId(), itemId);
    }

}
