package de.unistuttgart.iste.meitrex.assignment_service.api;

import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.ExerciseGradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.SubexerciseGradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.service.AssignmentService;
import de.unistuttgart.iste.meitrex.assignment_service.service.GradingService;
import de.unistuttgart.iste.meitrex.assignment_service.test_utils.TestUtils;
import de.unistuttgart.iste.meitrex.common.testutil.GraphQlApiTest;
import de.unistuttgart.iste.meitrex.common.testutil.InjectCurrentUserHeader;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.Commit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static de.unistuttgart.iste.meitrex.common.testutil.TestUsers.userWithMembershipInCourseWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@GraphQlApiTest
public class ImportGradingsForAssignmentTest {

    @Autowired
    private GradingService gradingService;
    private final UUID courseId = UUID.randomUUID();

    @InjectCurrentUserHeader
    private final LoggedInUser loggedInUser = userWithMembershipInCourseWithId(courseId, LoggedInUser.UserRoleInCourse.ADMINISTRATOR);

    @Autowired
    private TestUtils testUtils;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private AssignmentMapper assignmentMapper;

    @Test
    @Transactional
    @Commit
    void testImportGradingsForAssignment(final GraphQlTester tester) {
        // tests for a sheet and grading made on local TMS setup

        // create sheetNo 1 in MEITREX

        UUID exerciseId1 = UUID.randomUUID();
        UUID exerciseId2 = UUID.randomUUID();
        UUID subexerciseId1 = UUID.randomUUID();
        UUID subexerciseId2 = UUID.randomUUID();
        UUID subexerciseId3 = UUID.randomUUID();
        UUID subexerciseId4 = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();

        final List<CreateSubexerciseInput> createSubexerciseInputList1 = List.of(
                CreateSubexerciseInput.builder()
                        .setItemId(subexerciseId1)
                        .setNumber("<")
                        .setTotalSubexerciseCredits(15)
                        .setParentExerciseId(exerciseId1)
                        .build(),
                CreateSubexerciseInput.builder()
                        .setItemId(subexerciseId2)
                        .setNumber("c")
                        .setTotalSubexerciseCredits(46)
                        .setParentExerciseId(exerciseId1)
                        .build(),
                CreateSubexerciseInput.builder()
                        .setItemId(subexerciseId3)
                        .setNumber("2")
                        .setTotalSubexerciseCredits(1)
                        .setParentExerciseId(exerciseId1)
                        .build()
        );

        final List<CreateSubexerciseInput> createSubexerciseInputList2 = List.of(
                CreateSubexerciseInput.builder()
                        .setItemId(subexerciseId4)
                        .setNumber("l,")
                        .setTotalSubexerciseCredits(0.5)
                        .setParentExerciseId(exerciseId2)
                        .build()
        );

        final List<CreateExerciseInput> createExerciseInputList = List.of(
                CreateExerciseInput.builder()
                        .setTotalExerciseCredits(62)
                        .setItemId(exerciseId1)
                        .setNumber("1")
                        .setSubexercises(createSubexerciseInputList1)
                        .build(),
                CreateExerciseInput.builder()
                        .setTotalExerciseCredits(0.5)
                        .setItemId(exerciseId2)
                        .setNumber("2")
                        .setSubexercises(createSubexerciseInputList2)
                        .build()
        );

        final CreateAssignmentInput createAssignmentInput = CreateAssignmentInput.builder()
                .setAssignmentType(AssignmentType.EXERCISE_SHEET)
                .setDate(OffsetDateTime.now())
                .setDescription("sheetNo 1.0")
                .setExternalId("657b4ae4-e341-441e-87b1-46b3306c5ef0")
                .setTotalCredits(62.5)
                .setExercises(createExerciseInputList)
                .build();

        Assignment assignment = assignmentService.createAssignment(courseId, assessmentId, createAssignmentInput, loggedInUser);

        // import gradings from TMS (the important part)

        gradingService.importGradingsForAssignment(assessmentId, loggedInUser);

        // querying the grading from the repo

        final String query2 = """
                query($assignmentId: UUID!, $studentId: UUID!) {
                    getGradingForAssignmentForStudent(assessmentId: $assignmentId, studentId: $studentId) {
                        assessmentId
                        studentId
                        date
                        achievedCredits
                        exerciseGradings {
                            itemId
                            studentId
                            achievedCredits
                            subexerciseGradings {
                                itemId
                                studentId
                                achievedCredits
                            }
                        }
                    }
                }
                """;

        UUID studentId = UUID.nameUUIDFromBytes("this needs to be changed".getBytes());

        Grading receivedGrading = tester.document(query2)
                .variable("assignmentId", assessmentId)
                .variable("studentId", studentId) // null because we can't map student ids yet
                .execute()
                .path("getGradingForAssignmentForStudent")
                .entity(Grading.class)
                .get();

        // create reference grading
        GradingEntity expectedGradingEntityOne = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assessmentId, studentId))
                .date(OffsetDateTime.now())
                .achievedCredits(40.5)
                .build();
        ExerciseGradingEntity exerciseGradingOne1 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getItemId(), studentId))
                .parentGrading(expectedGradingEntityOne)
                .achievedCredits(40)
                .build();
        exerciseGradingOne1.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(0).getItemId(), studentId),
                                14, exerciseGradingOne1),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(1).getItemId(), studentId),
                                25, exerciseGradingOne1),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(2).getItemId(), studentId),
                                1, exerciseGradingOne1)
                )
        );
        ExerciseGradingEntity exerciseGradingOne2 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignment.getExercises().getLast().getItemId(), studentId))
                .parentGrading(expectedGradingEntityOne)
                .achievedCredits(0.5)
                .build();
        exerciseGradingOne2.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getLast().getSubexercises().getFirst().getItemId(), studentId),
                                0.5, exerciseGradingOne2)
                )
        );
        expectedGradingEntityOne.setExerciseGradings(List.of(exerciseGradingOne1, exerciseGradingOne2));

        // check if gradings match

        // times need to be adjusted because repository (presumably) rounds to milliseconds and converts to UTC
        expectedGradingEntityOne.setDate(expectedGradingEntityOne.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));
        receivedGrading.setDate(receivedGrading.getDate().truncatedTo(ChronoUnit.MILLIS).withOffsetSameInstant(ZoneOffset.UTC));

        assertThat(assignmentMapper.gradingEntityToDto(expectedGradingEntityOne), is(receivedGrading));

        // fails for two reasons:  - date is not correct (as is expected from code)
        //                         - achievedExerciseCredits are 0 from TMS (makes more sense to fix in TMS than to work around in MEITREX)


        /*
        Currently can't change studentId in GradingService, so can't check a second grading.

        // check second grading
        // create reference grading
        GradingEntity expectedGradingEntityTwo = GradingEntity.builder()
                .primaryKey(new GradingEntity.PrimaryKey(assessmentId, studentId))
                .date(OffsetDateTime.now())
                .achievedCredits(40.5)
                .build();
        ExerciseGradingEntity exerciseGradingTwo1 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getItemId(), studentId))
                .parentGrading(expectedGradingEntityTwo)
                .achievedCredits(40)
                .build();
        exerciseGradingTwo1.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(0).getItemId(), studentId),
                                14, exerciseGradingTwo1),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(1).getItemId(), studentId),
                                25, exerciseGradingTwo1),
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getFirst().getSubexercises().get(2).getItemId(), studentId),
                                1, exerciseGradingTwo1)
                )
        );
        ExerciseGradingEntity exerciseGradingTwo2 = ExerciseGradingEntity.builder()
                .primaryKey(new ExerciseGradingEntity.PrimaryKey(assignment.getExercises().getLast().getItemId(), studentId))
                .parentGrading(expectedGradingEntityTwo)
                .achievedCredits(0.5)
                .build();
        exerciseGradingTwo2.setSubexerciseGradings(
                List.of(
                        new SubexerciseGradingEntity(new SubexerciseGradingEntity.PrimaryKey(assignment.getExercises().getLast().getSubexercises().getFirst().getItemId(), studentId),
                                0.5, exerciseGradingTwo2)
                )
        );
        expectedGradingEntityTwo.setExerciseGradings(List.of(exerciseGradingTwo1, exerciseGradingTwo2));

        */

    }

}
