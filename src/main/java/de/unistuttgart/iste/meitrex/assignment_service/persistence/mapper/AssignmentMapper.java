package de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.CodeAssignmentMetadataEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.ExerciseGradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.SubexerciseGradingEntity;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.spi.MatchingStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AssignmentMapper {

    private final ModelMapper modelMapper;

    public Assignment assignmentEntityToDto(AssignmentEntity assignmentEntity) {
        if (assignmentEntity == null) {
            return null;
        }

        MatchingStrategy originalStrategy = modelMapper.getConfiguration().getMatchingStrategy();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Assignment mappedAssignment = modelMapper.map(assignmentEntity, Assignment.class);

        if (assignmentEntity.getAssignmentType() != AssignmentType.CODE_ASSIGNMENT) {
            mappedAssignment.setExercises(assignmentEntity.getExercises().stream().map(this::exerciseEntityToDto).toList());
        } else {
            CodeAssignmentMetadataEntity meta = assignmentEntity.getCodeAssignmentMetadata();
            mappedAssignment.setAssignmentLink(meta.getAssignmentLink());
            mappedAssignment.setInvitationLink(meta.getInvitationLink());
            mappedAssignment.setReadmeHtml(meta.getReadmeHtml());
        }

        modelMapper.getConfiguration().setMatchingStrategy(originalStrategy);
        return mappedAssignment;
    }

    public AssignmentEntity assignmentDtoToEntity(Assignment assignment) {
        AssignmentEntity mappedAssignment = modelMapper.map(assignment, AssignmentEntity.class);

        if (mappedAssignment.getAssignmentType() != AssignmentType.CODE_ASSIGNMENT) {
            for (final ExerciseEntity exerciseEntity : mappedAssignment.getExercises()) {
                exerciseEntity.setParentAssignment(mappedAssignment);
                for (final SubexerciseEntity subexerciseEntity : exerciseEntity.getSubexercises()) {
                    subexerciseEntity.setParentExercise(exerciseEntity);
                }
            }
        }

        return mappedAssignment;
    }

    public AssignmentEntity createAssignmentInputToEntity(final CreateAssignmentInput createAssignmentInput) {
        AssignmentEntity mappedAssignmentEntity = modelMapper.map(createAssignmentInput, AssignmentEntity.class);

        if (mappedAssignmentEntity.getAssignmentType() != AssignmentType.CODE_ASSIGNMENT) {
            for (final ExerciseEntity exerciseEntity : mappedAssignmentEntity.getExercises()) {
                exerciseEntity.setParentAssignment(mappedAssignmentEntity);
                for (final SubexerciseEntity subexerciseEntity : exerciseEntity.getSubexercises()) {
                    subexerciseEntity.setParentExercise(exerciseEntity);
                }
            }
        }

        return mappedAssignmentEntity;
    }

    public ExerciseEntity createExerciseInputToEntity(final CreateExerciseInput createExerciseInput) {
        ExerciseEntity mappedExerciseEntity = modelMapper.map(createExerciseInput, ExerciseEntity.class);

        for (final SubexerciseEntity subexerciseEntity : mappedExerciseEntity.getSubexercises()) {
            subexerciseEntity.setParentExercise(mappedExerciseEntity);
        }

        return mappedExerciseEntity;
    }

    public ExerciseEntity updateExerciseInputToEntity(final UpdateExerciseInput updateExerciseInput) {
        ExerciseEntity mappedExerciseEntity = modelMapper.map(updateExerciseInput, ExerciseEntity.class);

        for (final SubexerciseEntity subexerciseEntity : mappedExerciseEntity.getSubexercises()) {
            subexerciseEntity.setParentExercise(mappedExerciseEntity);
        }

        return mappedExerciseEntity;
    }

    public SubexerciseEntity createSubexerciseInputToEntity(final CreateSubexerciseInput createSubexerciseInput) {
        SubexerciseEntity mappedSubexerciseEntity = modelMapper.map(createSubexerciseInput, SubexerciseEntity.class);
        return mappedSubexerciseEntity;
    }

    public SubexerciseEntity updateSubexerciseInputToEntity(final UpdateSubexerciseInput updateSubexerciseInput) {
        SubexerciseEntity mappedSubexerciseEntity = modelMapper.map(updateSubexerciseInput, SubexerciseEntity.class);
        return mappedSubexerciseEntity;
    }

    public Exercise exerciseEntityToDto(final ExerciseEntity exerciseEntity) {
        Exercise mappedExercise = modelMapper.map(exerciseEntity, Exercise.class);
        mappedExercise.setSubexercises(exerciseEntity.getSubexercises().stream().map(this::subexerciseEntityToDto).toList());
        return mappedExercise;
    }

    public Subexercise subexerciseEntityToDto(final SubexerciseEntity subexerciseEntity) {
        return modelMapper.map(subexerciseEntity, Subexercise.class);
    }

    public Grading gradingEntityToDto(final GradingEntity gradingEntity) {
        Grading mappedGrading = modelMapper.map(gradingEntity, Grading.class);

        mappedGrading.setAssessmentId(gradingEntity.getPrimaryKey().getAssessmentId());
        mappedGrading.setStudentId(gradingEntity.getPrimaryKey().getStudentId());

        if (gradingEntity.getExerciseGradings() != null) {
            mappedGrading.setExerciseGradings(
                    gradingEntity.getExerciseGradings().stream()
                            .map(this::exerciseGradingEntityToDto)
                            .toList()
            );
        } else {
            mappedGrading.setExerciseGradings(Collections.emptyList());
        }

        return mappedGrading;
    }


    public ExerciseGrading exerciseGradingEntityToDto(final ExerciseGradingEntity exerciseGradingEntity) {
        ExerciseGrading mappedExerciseGrading = modelMapper.map(exerciseGradingEntity, ExerciseGrading.class);

        mappedExerciseGrading.setItemId(exerciseGradingEntity.getPrimaryKey().getItemId());
        mappedExerciseGrading.setStudentId(exerciseGradingEntity.getPrimaryKey().getStudentId());

        mappedExerciseGrading.setSubexerciseGradings(exerciseGradingEntity.getSubexerciseGradings().stream().map(this::subexerciseGradingEntityToDto).toList());

        return mappedExerciseGrading;
    }

    public SubexerciseGrading subexerciseGradingEntityToDto(final SubexerciseGradingEntity subexerciseGradingEntity) {
        SubexerciseGrading mappedSubexerciseGrading = modelMapper.map(subexerciseGradingEntity, SubexerciseGrading.class);
        mappedSubexerciseGrading.setItemId(subexerciseGradingEntity.getPrimaryKey().getItemId());
        mappedSubexerciseGrading.setStudentId(subexerciseGradingEntity.getPrimaryKey().getStudentId());
        return mappedSubexerciseGrading;
    }


    public StudentMappingEntity studentMappingInputToEntity(final StudentMappingInput studentMappingInput) {
        return modelMapper.map(studentMappingInput, StudentMappingEntity.class);
    }

    public ManualMappingInstance manualMappingInstanceEntityToDto(final ManualMappingInstanceEntity manualMappingInstanceEntity) {
        return modelMapper.map(manualMappingInstanceEntity, ManualMappingInstance.class);
    }
}
