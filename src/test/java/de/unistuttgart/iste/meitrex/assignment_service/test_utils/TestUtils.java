package de.unistuttgart.iste.meitrex.assignment_service.test_utils;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
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
    public AssignmentEntity populateAssignmentRepository(final AssignmentRepository assignmentRepository, final UUID courseId) {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW) // Required so the data is committed to the repo before the
    // rest of the test method which calls this method is executed.
    public List<GradingEntity> populateGradingRepository(final GradingRepository gradingRepository, final AssignmentEntity assignmentEntity) {

        final UUID studentId1 = UUID.randomUUID();
        // current grading of student 1
        GradingEntity grading1 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId1))
                .date(OffsetDateTime.now())
                .achievedCredits(35)
                .build();
        ExerciseGradingEntity exerciseGrading1 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getItemId(), studentId1))
                .parentGrading(grading1)
                .achievedCredits(35)
                .build();
        exerciseGrading1.setSubexerciseGradings(
                        List.of(
                                new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getItemId(), studentId1),
                                        17, exerciseGrading1),
                                new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getItemId(), studentId1),
                                        18, exerciseGrading1)
                        )
                );
        grading1.setExerciseGradings(List.of(exerciseGrading1));

        // old grading of student 1
        GradingEntity grading2 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId1))
                .date(OffsetDateTime.now().minusDays(2))
                .achievedCredits(15)
                .build();
        ExerciseGradingEntity exerciseGrading2 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getItemId(), studentId1))
                .parentGrading(grading2)
                .achievedCredits(15)
                .build();
        exerciseGrading2.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getItemId(), studentId1),
                                7, exerciseGrading2),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getItemId(), studentId1),
                                8, exerciseGrading2)
                )
        );
        grading2.setExerciseGradings(List.of(exerciseGrading2));

        // grading of another student
        final UUID studentId2 = UUID.randomUUID();
        GradingEntity grading3 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId2))
                .date(OffsetDateTime.now())
                .achievedCredits(3)
                .build();
        ExerciseGradingEntity exerciseGrading3 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getItemId(), studentId2))
                .parentGrading(grading3)
                .achievedCredits(3)
                .build();
        exerciseGrading3.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getFirst().getItemId(), studentId2),
                                0, exerciseGrading3),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignmentEntity.getExercises().getFirst().getSubexercises().getLast().getItemId(), studentId2),
                                3, exerciseGrading3)
                )
        );
        grading3.setExerciseGradings(List.of(exerciseGrading3));

        return List.of(gradingRepository.save(grading1), gradingRepository.save(grading2), gradingRepository.save(grading3));
    }
}