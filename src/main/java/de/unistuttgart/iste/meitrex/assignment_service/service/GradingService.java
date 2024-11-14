package de.unistuttgart.iste.meitrex.assignment_service.service;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.exception.NoAccessToCourseException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalAssignment;
import de.unistuttgart.iste.meitrex.generated.dto.Grading;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.json.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static de.unistuttgart.iste.meitrex.common.user_handling.UserCourseAccessValidator.validateUserHasAccessToCourse;

@Service
@RequiredArgsConstructor
public class GradingService {

    private final GradingRepository gradingRepository;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentValidator assignmentValidator;
    private final TopicPublisher topicPublisher;
    private final AssignmentService assignmentService;

    public Grading getGradingForAssignmentForStudent(final UUID assignmentId, final UUID studentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignment = assignmentService.requireAssignmentExists(assignmentId); // throws EntityNotFoundException "Assignment with assessmentId %s not found"
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.STUDENT, assignment.getCourseId());
        } catch (final NoAccessToCourseException ex) {
            return null;
        }
        GradingEntity gradingEntity = gradingRepository.findById(new GradingEntity.PrimaryKey(assignmentId, studentId))
                .orElseThrow(() -> new EntityNotFoundException("Grading with assessmentId %s and studentId %s not found".formatted(assignmentId, studentId)));

        return assignmentMapper.gradingEntityToDto(gradingEntity);
    }

    private void getGradingsForAssignment(final UUID assignmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignment = assignmentService.requireAssignmentExists(assignmentId); // throws EntityNotFoundException "Assignment with assessmentId %s not found"
        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.STUDENT, assignment.getCourseId());

        String externalId = assignment.getExternalId();

        // these need to be set!
        String authToken = "";
        String basePath = "";

        String body;
        CompletableFuture<String> response;
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(basePath + "api/grading/handIn/" + externalId))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + authToken)
                    .build();
            response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
            body = response.join();
        }

        List<GradingEntity> gradingEntities = parseStringIntoGradingEntityList(body, assignment);

        // TODO add them to repo, publish user progress events
    }

    private List<GradingEntity> parseStringIntoGradingEntityList(final String string, final AssignmentEntity assignmentEntity) {
        JSONArray gradingArray = new JSONArray(string);
        final List<GradingEntity> gradingEntityList = new ArrayList<>(gradingArray.length());

        for (int i = 0; i < gradingArray.length(); i++) {
            gradingEntityList.add(parseIntoGradingEntity(gradingArray.getJSONObject(i), assignmentEntity));
        }
        return gradingEntityList;
    }

    private GradingEntity parseIntoGradingEntity(final JSONObject jsonObject, final AssignmentEntity assignmentEntity) {
        final GradingEntity gradingEntity = new GradingEntity();

        String externalStudentId = jsonObject.getString("studentId"); // TODO match this to Meitrex student id
        UUID studentId = null;

        gradingEntity.setPrimaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getId(), studentId));
        gradingEntity.setAchievedCredits(jsonObject.getDouble("points"));
        gradingEntity.setDate(OffsetDateTime.now()); // TODO can this be more precise?

        JSONArray exerciseArray = jsonObject.getJSONArray("exerciseGradings");
        List<ExerciseGradingEntity> exerciseGradingEntities = new ArrayList<>(exerciseArray.length());
        List<ExerciseEntity> exerciseEntities = assignmentEntity.getExercises();

        if (exerciseEntities.size() != exerciseArray.length()) {
            throw new ValidationException("Assignment and grading don't have the same number of exercises");
        }

        for (int i = 0; i < exerciseArray.length(); i++) {
            JSONObject jsonExercise = exerciseArray.getJSONArray(i).getJSONObject(1); // 1 because exercise gradings are [string, exerciseGrading]-pairs in TMS
            ExerciseEntity exerciseEntity = exerciseEntities.get(i);
            ExerciseGradingEntity exerciseGradingEntity = ExerciseGradingEntity.builder()
                    .parentGrading(gradingEntity)
                    .achievedCredits(jsonExercise.getDouble("points")) // TODO this might be buggy in TMS (says 0 even if it should be more)
                    .primaryKey(new ExerciseGradingEntity.PrimaryKey(exerciseEntity.getId(), studentId))
                    .build();

            JSONArray subexerciseArray = jsonExercise.getJSONArray("subExercisePoints");
            List<SubexerciseGradingEntity> subexerciseGradingEntities = new ArrayList<>(subexerciseArray.length());
            List<SubexerciseEntity> subexerciseEntities = exerciseEntity.getSubexercises();

            if (subexerciseEntities.size() != subexerciseArray.length()) {
                throw new ValidationException("One exercise in assignment and grading don't have the same number of subexercises");
            }

            for (int j = 0; j < subexerciseArray.length(); j++) {
                SubexerciseEntity subexerciseEntity = subexerciseEntities.get(j);
                SubexerciseGradingEntity subexerciseGradingEntity = SubexerciseGradingEntity.builder()
                        .achievedCredits(subexerciseArray.getJSONArray(j).getDouble(1)) // 1 because subexercise gradings are [string, subexerciseGrading]-pairs in TMS
                        .parentExerciseGrading(exerciseGradingEntity)
                        .primaryKey(new SubexerciseGradingEntity.PrimaryKey(subexerciseEntity.getId(), studentId))
                        .build();
                subexerciseGradingEntities.add(subexerciseGradingEntity);
            }

            exerciseGradingEntity.setSubexerciseGradings(subexerciseGradingEntities);
            exerciseGradingEntities.add(exerciseGradingEntity);
        }

        gradingEntity.setExerciseGradings(exerciseGradingEntities);
        return gradingEntity;
    }

    public List<ExternalAssignment> getExternalAssignments(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
        } catch (final NoAccessToCourseException ex) {
            return null;
        }

        // these need to be set!
        String authToken = "";
        String basePath = "";

        String body;
        CompletableFuture<String> response;
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(basePath + "api/sheet"))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + authToken)
                    .build();
            response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
            body = response.join();
        }
        if (body == null) return null;
        List<ExternalAssignment> externalAssignments = this.parseStringIntoExternalAssignmentList(body);

        return externalAssignments;
    }

    private List<ExternalAssignment> parseStringIntoExternalAssignmentList(final String string) {

        JSONArray sheetArray = new JSONArray(string);
        List<ExternalAssignment> externalAssignmentList = new ArrayList<>(sheetArray.length());

        for (int i = 0; i < sheetArray.length(); i++) {
            ExternalAssignment externalAssignment = new ExternalAssignment();
            JSONObject sheetObject = sheetArray.getJSONObject(i);
            externalAssignment.setExternalId(sheetObject.getString("id"));
            externalAssignment.setSheetNo(sheetObject.getDouble("sheetNo"));
            externalAssignmentList.add(externalAssignment);
        }
        return externalAssignmentList;
    }

}
