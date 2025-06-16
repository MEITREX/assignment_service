package de.unistuttgart.iste.meitrex.assignment_service.test_utils;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.CodeAssignmentMetadataEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.CodeAssignmentGradingMetadataEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.ExerciseGradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.SubexerciseGradingEntity;
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
        assignmentEntity.setTotalCredits(50.0);
        assignmentEntity.setExternalId("assignment1externalId");
        assignmentEntity.setRequiredPercentage(0.5);

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
    public AssignmentEntity populateAssignmentRepositoryWithCodeAssignment(final AssignmentRepository assignmentRepository, final UUID courseId) {
        AssignmentEntity assignmentEntity = new AssignmentEntity();
        assignmentEntity.setAssessmentId(UUID.randomUUID());
        assignmentEntity.setCourseId(courseId);
        assignmentEntity.setAssignmentType(AssignmentType.CODE_ASSIGNMENT);
        assignmentEntity.setCodeAssignmentMetadata(CodeAssignmentMetadataEntity.builder()
                .assignment(assignmentEntity)
                .assignmentLink("assignmentLink")
                .invitationLink("invitationLink").build());
        assignmentEntity.setTotalCredits(10.0);
        assignmentEntity.setExternalId("assignment1externalId");
        assignmentEntity.setRequiredPercentage(0.5);

        return assignmentRepository.save(assignmentEntity);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW) // Required so the data is committed to the repo before the
    // rest of the test method which calls this method is executed.
    public List<GradingEntity> populateGradingRepository(final GradingRepository gradingRepository, final AssignmentEntity assignmentEntity, final UUID studentId1) {

        // current grading of student 1
        GradingEntity grading1 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId1))
                .date(OffsetDateTime.now())
                .achievedCredits(35.0)
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
                .achievedCredits(15.0)
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

        return List.of(gradingRepository.save(grading1), gradingRepository.save(grading2));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW) // Required so the data is committed to the repo before the
    // rest of the test method which calls this method is executed.
    public List<GradingEntity> populateGradingRepositoryForCodeAssignment(final GradingRepository gradingRepository, final AssignmentEntity assignmentEntity, final UUID studentId1) {

        // current grading of student 1
        GradingEntity grading1 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId1))
                .date(OffsetDateTime.now())
                .achievedCredits(35.0)
                .build();

        CodeAssignmentGradingMetadataEntity metadata1 = CodeAssignmentGradingMetadataEntity.builder()
                .id(grading1.getPrimaryKey())
                .grading(grading1)
                .repoLink("link")
                .build();

        grading1.setCodeAssignmentGradingMetadata(metadata1);

        // old grading of student 1
        GradingEntity grading2 = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getAssessmentId(), studentId1))
                .date(OffsetDateTime.now().minusDays(2))
                .achievedCredits(15.0)
                .build();

        CodeAssignmentGradingMetadataEntity metadata2 = CodeAssignmentGradingMetadataEntity.builder()
                .id(grading2.getPrimaryKey())
                .grading(grading2)
                .repoLink("link")
                .build();

        grading2.setCodeAssignmentGradingMetadata(metadata2);

        return List.of(gradingRepository.save(grading1), gradingRepository.save(grading2));
    }
}