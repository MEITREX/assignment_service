package de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.generated.dto.Assignment;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.generated.dto.CreateAssignmentInput;
import de.unistuttgart.iste.meitrex.generated.dto.Subexercise;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentMapper {

    private final ModelMapper modelMapper;

    public Assignment entityToDto(AssignmentEntity assignmentEntity) {
        // add specific mapping here if needed
        return modelMapper.map(assignmentEntity, Assignment.class);
    }

    public Assignment assignmentEntityToDto(AssignmentEntity assignmentEntity) {
        return entityToDto(assignmentEntity);
    }

    public AssignmentEntity dtoToEntity(Assignment assignment) {
        // add specific mapping here if needed
        return modelMapper.map(assignment, AssignmentEntity.class);
    }

    public AssignmentEntity assignmentDtoToEntity(Assignment assignment) {
        return dtoToEntity(assignment);
    }

    public AssignmentEntity createAssignmentInputToEntity(final CreateAssignmentInput createAssignmentInput) {
        AssignmentEntity mappedAssignmentEntity = modelMapper.map(createAssignmentInput, AssignmentEntity.class);

        for (ExerciseEntity exerciseEntity : mappedAssignmentEntity.getExercises()) {
            exerciseEntity.setParentAssignment(mappedAssignmentEntity);
            for (SubexerciseEntity subexerciseEntity : exerciseEntity.getSubexercises()) {
                subexerciseEntity.setParentExercise(exerciseEntity);
            }
        }

        return mappedAssignmentEntity;
    }

}
