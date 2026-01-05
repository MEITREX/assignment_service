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

    public UmlExercise entityToDto(UmlExerciseEntity entity) {
        if (entity == null) {
            return null;
        }
        UmlExercise dto = modelMapper.map(entity, UmlExercise.class);

        // Ensure nested lists are mapped correctly
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
        if (entity.getFeedback() != null) {
            dto.setFeedback(feedbackEntityToDto(entity.getFeedback()));
        }
        return dto;
    }

    public UmlFeedback feedbackEntityToDto(UmlFeedbackEntity entity) {
        return modelMapper.map(entity, UmlFeedback.class);
    }

    public UmlExerciseEntity createInputToEntity(CreateUmlExerciseInput input) {
        return modelMapper.map(input, UmlExerciseEntity.class);
    }
}