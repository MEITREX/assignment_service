package de.unistuttgart.iste.meitrex.assignment_service.test_utils;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TestUtils {

    /**
     * Helper method which creates an assignment and saves it to the repository.
     *
     * @param assignmentRepository The repository to save the assignment to.
     * @return Returns the created assignment.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW) // Required so the data is committed to the repo before the
    // rest of the test method which calls this method is executed.
    public AssignmentEntity populateAssignmentRepository(AssignmentRepository assignmentRepository, UUID courseId) {
        AssignmentEntity assignmentEntity = new AssignmentEntity();
        assignmentEntity.setAssessmentId(UUID.randomUUID());
        assignmentEntity.setCourseId(courseId);
        assignmentEntity.setAssignmentType(AssignmentType.EXERCISE_SHEET);
        assignmentEntity.setTotalCredits(50f);

        ExerciseEntity exerciseEntity = new ExerciseEntity();
        exerciseEntity.setParentAssignment(assignmentEntity);
        exerciseEntity.setNumber("1");
        exerciseEntity.setTotalExerciseCredits(50f);
        exerciseEntity.setItemId(UUID.randomUUID());
        exerciseEntity.setSubexercises(
            List.of(
                new SubexerciseEntity(UUID.randomUUID(), 30f, "a", null, exerciseEntity),
                new SubexerciseEntity(UUID.randomUUID(), 20f, "b", null, exerciseEntity)
            )
        );

        assignmentEntity.setExercises(List.of(exerciseEntity));

        return assignmentRepository.save(assignmentEntity);
    }

}