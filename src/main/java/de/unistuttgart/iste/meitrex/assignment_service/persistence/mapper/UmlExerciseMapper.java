package de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlFeedbackEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSolutionEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlStudentSubmissionEntity;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UmlExerciseMapper {

    private final ModelMapper modelMapper;

    /**
     * Maps the JPA UmlDiagram entity to the GraphQL DTO.
     */
    public de.unistuttgart.iste.meitrex.generated.dto.UmlDiagram diagramToDto(
            de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlDiagram entity) {
        if (entity == null) return null;
        return modelMapper.map(entity, de.unistuttgart.iste.meitrex.generated.dto.UmlDiagram.class);
    }

    /**
     * Maps the GraphQL UmlDiagramInput to the JPA entity.
     */
    public de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlDiagram inputToEntity(
            UmlDiagramInput input) {
        if (input == null) return null;
        return modelMapper.map(input,
                de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.umlExercise.UmlDiagram.class);
    }

    public UmlExercise entityToDto(UmlExerciseEntity entity) {
        if (entity == null) return null;

        UmlExercise dto = modelMapper.map(entity, UmlExercise.class);

        if (entity.getTutorSolution() != null) {
            dto.setTutorSolution(diagramToDto(entity.getTutorSolution()));
        }

        if (entity.getStudentSubmissions() != null) {
            dto.setStudentSubmissions(entity.getStudentSubmissions().stream()
                    .map(this::submissionEntityToDto)
                    .toList());
        }
        return dto;
    }

    public UmlStudentSubmission submissionEntityToDto(UmlStudentSubmissionEntity entity) {
        UmlStudentSubmission dto = modelMapper.map(entity, UmlStudentSubmission.class);
        if (entity.getSolutions() != null) {
            dto.setSolutions(entity.getSolutions().stream()
                    .map(this::solutionEntityToDto)
                    .toList());
        }
        return dto;
    }

    public UmlStudentSolution solutionEntityToDto(UmlStudentSolutionEntity entity) {
        UmlStudentSolution dto = modelMapper.map(entity, UmlStudentSolution.class);

        // Explicitly map the embedded student diagram
        if (entity.getDiagram() != null) {
            dto.setDiagram(diagramToDto(entity.getDiagram()));
        }

        if (entity.getFeedback() != null) {
            dto.setFeedback(feedbackEntityToDto(entity.getFeedback()));
        }
        return dto;
    }

    public UmlFeedback feedbackEntityToDto(UmlFeedbackEntity entity) {
        return modelMapper.map(entity, UmlFeedback.class);
    }

    public UmlExerciseEntity createInputToEntity(CreateUmlExerciseInput input) {
        UmlExerciseEntity entity = modelMapper.map(input, UmlExerciseEntity.class);
        if (input.getTutorSolution() != null) {
            entity.setTutorSolution(inputToEntity(input.getTutorSolution()));
        }
        return entity;
    }
}