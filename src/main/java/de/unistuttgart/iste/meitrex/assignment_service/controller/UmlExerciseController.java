package de.unistuttgart.iste.meitrex.assignment_service.controller;

import de.unistuttgart.iste.meitrex.assignment_service.service.UmlExerciseService;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UmlExerciseController {

    private final UmlExerciseService umlExerciseService;

    @MutationMapping(name = "_internal_noauth_createUmlExercise")
    public UmlExercise createUmlExercise(@Argument final UUID courseId,
                                         @Argument final UUID assessmentId,
                                         @Argument final CreateUmlExerciseInput input) {
        return umlExerciseService.createExercise(courseId, assessmentId, input);
    }

    @MutationMapping
    public UmlExerciseMutation mutateUmlExercise(@Argument final UUID assessmentId,
                                                 @ContextValue final LoggedInUser currentUser) {
        return umlExerciseService.mutateUmlExercise(assessmentId, currentUser);
    }

    @SchemaMapping(typeName = "UmlExerciseMutation")
    public UmlExercise updateTutorSolution(final UmlExerciseMutation mutation,
                                           @Argument final String tutorSolution) {
        return umlExerciseService.updateTutorSolution(mutation.getAssessmentId(), tutorSolution);
    }

    @SchemaMapping(typeName = "UmlExerciseMutation")
    public UmlStudentSolution createUmlSolution(final UmlExerciseMutation mutation,
                                                @Argument UUID studentId,
                                                @Argument boolean createFromPrevious) {
        log.info("Mutation: createUmlSolution for assessmentId={}, studentId={}", mutation.getAssessmentId(), studentId);
        return umlExerciseService.createNewSolution(mutation.getAssessmentId(), studentId, createFromPrevious);
    }

    @SchemaMapping(typeName = "UmlExerciseMutation")
    public UmlStudentSolution saveStudentSolution(final UmlExerciseMutation mutation,
                                                    @Argument final UUID studentId,
                                                    @Argument final String diagram,
                                                    @Argument final UUID solutionId,
                                                    @Argument final boolean submitted) {
        return umlExerciseService.saveStudentSolution(
                mutation.getAssessmentId(), studentId, diagram, solutionId, submitted);
    }

    @QueryMapping
    public UmlExercise getUmlExerciseByAssessmentId(@Argument UUID assessmentId) {
        return umlExerciseService.getExerciseByAssessmentId(assessmentId);
    }

    @SchemaMapping(typeName = "UmlExercise")
    public List<UmlStudentSolution> solutionsByStudent(UmlExercise exercise, @Argument UUID studentId) {
        return umlExerciseService.getSolutionsByStudent(exercise, studentId);
    }

    @SchemaMapping(typeName = "UmlExercise")
    public UmlStudentSolution latestSolution(UmlExercise exercise, @Argument UUID studentId) {
        return umlExerciseService.getSolutionsByStudent(exercise, studentId).stream()
            .findFirst()
            .orElse(null);
    }

    @MutationMapping
    public UmlStudentSolution evaluateLatestSolution(
            @Argument UUID assessmentId,
            @Argument UUID studentId,
            @Argument String semanticModel) {
        return umlExerciseService.evaluateLatestSolution(assessmentId, studentId, semanticModel);
    }
}
