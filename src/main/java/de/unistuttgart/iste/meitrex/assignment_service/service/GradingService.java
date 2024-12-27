package de.unistuttgart.iste.meitrex.assignment_service.service;


import de.unistuttgart.iste.meitrex.assignment_service.exception.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ManualMappingInstanceRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.StudentMappingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.UnfinishedGradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.event.ContentProgressedEvent;
import de.unistuttgart.iste.meitrex.common.event.Response;
import de.unistuttgart.iste.meitrex.common.exception.NoAccessToCourseException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.ClientGraphQlResponse;
import reactor.core.publisher.SynchronousSink;
import org.springframework.stereotype.Service;
import org.json.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.*;
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
    private final StudentMappingRepository studentMappingRepository;
    private final ManualMappingInstanceRepository manualMappingInstanceRepository;
    private final UnfinishedGradingRepository unfinishedGradingRepository;

    private final GraphQlClient userServiceClient;

    // these need to be set!
    private static final String authToken = "";
    private static final String basePath = "";

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

    /**
     * Handles importing all gradings for one assignment from TMS.
     * <br><br>
     * All gradings for one assignment are requested from TMS via HTTP. The response is parsed to gradingEntities. <br>
     * These gradingEntities are saved in the grading repository. <br>
     * A contentProgressedEvent is published for each grading (i.e. each student).
     *
     * @param assignmentId id of the assignment of which the gradings should be imported
     * @param currentUser the user requesting the import (needs to be admin)
     */
    public void importGradingsForAssignment(final UUID assignmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignment = assignmentService.requireAssignmentExists(assignmentId); // throws EntityNotFoundException "Assignment with assessmentId %s not found"
        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, assignment.getCourseId());

        String externalId = assignment.getExternalId();

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

        if (body == null) {
            // something went wrong, can't do anything, try again next time
            throw new RuntimeException(
                    new ExternalPlatformConnectionException("Querying gradings for externalAssignmentId %s went wrong.".formatted(externalId))); // wrapping exception
            // return; TODO return or throw wrapped exception?
        }

        final List<Map<String, Object>> meitrexStudentInfoList;
        try {
            meitrexStudentInfoList = getMeitrexStudentInfoList();
        } catch (UserServiceConnectionException e){
            throw new RuntimeException(e); // wrapping exception
            // return; TODO return or throw wrapped exception?
        }

        List<GradingEntity> gradingEntityList = parseStringIntoGradingEntityList(body, assignment, meitrexStudentInfoList);

        for (GradingEntity gradingEntity : gradingEntityList) {
            gradingRepository.save(gradingEntity);
            logGradingImported(gradingEntity);
        }

    }

    /**
     * Parses a JSON String to a list of grading entities.
     *
     * @param string JSON Array containing a list of TMS-gradings
     * @param assignmentEntity the assignment id which the gradings belong to
     * @return List of parsed grading entities
     */
    private List<GradingEntity> parseStringIntoGradingEntityList(final String string, final AssignmentEntity assignmentEntity, final List<Map<String, Object>> meitrexStudentInfoList) {
        JSONArray gradingArray = new JSONArray(string);
        final List<GradingEntity> gradingEntityList = new ArrayList<>(gradingArray.length());
        GradingEntity gradingEntity;
        for (int i = 0; i < gradingArray.length(); i++) {
            JSONObject jsonObject = gradingArray.getJSONObject(i);
            try {
                gradingEntity = parseIntoGradingEntity(jsonObject, assignmentEntity, meitrexStudentInfoList);
                gradingEntityList.add(gradingEntity);
            } catch (ManualMappingRequiredException e) {
                // fine, will be handled by manual mapping of admin
            } catch (ExternalPlatformConnectionException e) {
                // can't be handled further, will be tried again when manual mapping happened
            }
        }
        return gradingEntityList;
    }

    /**
     * Parses a single JSON Object into a grading entity.
     *
     * @param jsonObject JSON Object containing a single TMS-grading
     * @param assignmentEntity the assignment id which the grading belongs to
     * @return parsed grading entity
     */
    private GradingEntity parseIntoGradingEntity(final JSONObject jsonObject, final AssignmentEntity assignmentEntity, final List<Map<String, Object>> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
        final GradingEntity gradingEntity = new GradingEntity();

        String externalStudentId = jsonObject.getString("studentId"); // TODO match this to Meitrex student id
        UUID studentId;
        try {
            studentId = getStudentIdFromExternalStudentId(externalStudentId, meitrexStudentInfoList);
        } catch (ManualMappingRequiredException e) {
            // ManualMappingInstance is added to repository, so that an admin can map manually
            JSONObject externalStudentInfo = e.getExternalStudentInfo();
            manualMappingInstanceRepository.save(ManualMappingInstanceEntity.fromJson(externalStudentInfo));

            // Grading is added to unfinished grading repository, so that it can be tried again, when a manual mapping was done.
            addToUnfinishedGradingRepository(jsonObject, assignmentEntity, externalStudentId);

            throw(e);

        } catch (ExternalPlatformConnectionException e) {
            // Grading is added to unfinished grading repository, so that it can be tried again, when a manual mapping was done.
            addToUnfinishedGradingRepository(jsonObject, assignmentEntity, externalStudentId);
            throw(e);
        }

        JSONObject gradingData = jsonObject.getJSONObject("gradingData");

        gradingEntity.setPrimaryKey(new GradingEntity.PrimaryKey(assignmentEntity.getId(), studentId));
        gradingEntity.setAchievedCredits(gradingData.getDouble("points"));
        gradingEntity.setDate(OffsetDateTime.now()); // TODO can this be more precise?

        JSONArray exerciseArray = gradingData.getJSONArray("exerciseGradings");
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

    private void addToUnfinishedGradingRepository(final JSONObject jsonObject, final AssignmentEntity assignmentEntity, final String externalStudentId) {
        // Grading is added to unfinished grading repository, so that it can be tried again, when a manual mapping was done.
        UnfinishedGradingEntity unfinishedGradingEntity;
        Optional<UnfinishedGradingEntity> foundEntityOptional = unfinishedGradingRepository.findById(new UnfinishedGradingEntity.PrimaryKey(externalStudentId, assignmentEntity.getAssessmentId()));
        if (foundEntityOptional.isPresent()) {
            unfinishedGradingEntity = foundEntityOptional.get();
            unfinishedGradingEntity.incrementNumberOfTries();
        } else {
            unfinishedGradingEntity = UnfinishedGradingEntity.fromJson(jsonObject, assignmentEntity.getAssessmentId());
        }
        unfinishedGradingRepository.save(unfinishedGradingEntity);
    }


    /**
     * Takes gradingEntity and publishes the {@link ContentProgressedEvent} to the dapr pubsub.
     *
     * @param gradingEntity gradingEntity containing all information
     */
    private void logGradingImported(final GradingEntity gradingEntity) {
        final AssignmentEntity assignmentEntity = assignmentService.requireAssignmentExists(gradingEntity.getId().getAssessmentId());
        assignmentValidator.validateGradingEntityFitsAssignmentEntity(assignmentEntity, gradingEntity);

        final double requiredPercentage = assignmentEntity.getRequiredPercentage() == null ? 0.5 : assignmentEntity.getRequiredPercentage();

        final double achievedCredits = gradingEntity.getAchievedCredits();
        final double totalCredits = assignmentEntity.getTotalCredits();

        final boolean success = achievedCredits >= requiredPercentage * totalCredits;
        final double correctness = totalCredits == 0 ? 1.0f : achievedCredits / totalCredits;

        // create Responses for each exercise and subexercise
        final List<Response> responses = new ArrayList<>();
        for (final ExerciseGradingEntity exerciseGradingEntity : gradingEntity.getExerciseGradings()) {
            final UUID exerciseId = exerciseGradingEntity.getPrimaryKey().getItemId();
            final ExerciseEntity exerciseEntity = assignmentService.findExerciseEntityInAssignmentEntity(exerciseId, assignmentEntity);
            final double totalExerciseCredits = exerciseEntity.getTotalExerciseCredits();
            final float achievedExercisePercentage = totalExerciseCredits == 0 ? 1.0f : (float) (exerciseGradingEntity.getAchievedCredits() / totalExerciseCredits);
            final Response exerciseResponse = new Response(exerciseId, achievedExercisePercentage);
            responses.add(exerciseResponse);

            for (final SubexerciseGradingEntity subexerciseGradingEntity : exerciseGradingEntity.getSubexerciseGradings()) {
                final UUID subexerciseId = subexerciseGradingEntity.getPrimaryKey().getItemId();
                final SubexerciseEntity subexerciseEntity = assignmentService.findSubexerciseEntityInExerciseEntity(subexerciseId, exerciseEntity);
                final double totalSubexerciseCredits = subexerciseEntity.getTotalSubexerciseCredits();
                final float achievedSubexercisePercentage = totalSubexerciseCredits == 0 ? 1.0f : (float) (subexerciseGradingEntity.getAchievedCredits() / totalSubexerciseCredits);
                final Response subexerciseResponse = new Response(subexerciseId, achievedSubexercisePercentage);
                responses.add(subexerciseResponse);
            }
        }

        // create new user progress event message
        final ContentProgressedEvent userProgressLogEvent = ContentProgressedEvent.builder()
                .userId(gradingEntity.getPrimaryKey().getStudentId())
                .contentId(assignmentEntity.getAssessmentId())
                .hintsUsed(0)
                .success(success)
                .timeToComplete(null)
                .correctness(correctness)
                .responses(responses)
                .build();

        // publish new user progress event message
        topicPublisher.notifyUserWorkedOnContent(userProgressLogEvent);
    }

    private UUID getStudentIdFromExternalStudentId(final String externalStudentId, final List<Map<String, Object>> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
        Optional<StudentMappingEntity> studentMappingEntity = studentMappingRepository.findById(externalStudentId);
        if (studentMappingEntity.isPresent()) {
            return studentMappingEntity.get().getMeitrexStudentId();
        }
        UUID newMeitrexStudentId = findNewStudentIdFromExternalStudentId(externalStudentId, meitrexStudentInfoList); // throws exception if nothing is found
        studentMappingRepository.save(new StudentMappingEntity(externalStudentId, newMeitrexStudentId));
        return newMeitrexStudentId;
    }

    private UUID findNewStudentIdFromExternalStudentId(final String externalStudentId, final List<Map<String, Object>> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
        JSONObject externalStudentInfo = getExternalStudentInfo(externalStudentId);

        // list is fetched from user service at the beginning, rather than for each grading
        // final List<Map<String, Object>> meitrexStudentInfoList = getMeitrexStudentInfoList();

        Object lastName = externalStudentInfo.get("lastname");
        Object firstName = externalStudentInfo.get("firstname");

        // filter by last name
        List<Map<String,Object>> filteredByLastName = meitrexStudentInfoList.stream()
                .filter(userInfo -> userInfo.get("lastName").equals(lastName))
                .toList();
        if (filteredByLastName.isEmpty()) {
            throw new ManualMappingRequiredException(externalStudentInfo);
        } else if (filteredByLastName.size() == 1) {
            return (UUID) filteredByLastName.getFirst().get("id");
        }

        // filter by first name, if there are still multiple candidates
        List<Map<String,Object>> filteredByFirstName = filteredByLastName.stream()
                .filter(userInfo -> userInfo.get("firstName").equals(firstName))
                .toList();
        if (filteredByFirstName.isEmpty()) {
            throw new ManualMappingRequiredException(externalStudentInfo);
        } else if (filteredByFirstName.size() == 1) {
            return (UUID) filteredByFirstName.getFirst().get("id");
        }

        // filter by more attributes like email, matriculation number etc. if there are still more candidates

        // if no match is found, the id needs to be mapped manually
        throw new ManualMappingRequiredException(externalStudentInfo);
    }

    private JSONObject getExternalStudentInfo(final String externalStudentId) throws ExternalPlatformConnectionException {
        String body;
        CompletableFuture<String> response;
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(basePath + "api/student/" + externalStudentId))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + authToken)
                    .build();
            response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
            body = response.join();
        }

        if (body == null) throw new ExternalPlatformConnectionException("Querying external student info for externalStudentId %s went wrong.".formatted(externalStudentId));

        return new JSONObject(body);
    }


    // TODO this whole thing should be in userService rather than here
    private List<Map<String, Object>> getMeitrexStudentInfoList() throws UserServiceConnectionException {
        String query = "findAllUserInfos"; // TODO doesn't exist currently
        String queryName = "findAllUserInfos";
        List<Map<String, Object>> meitrexStudentInfoList = userServiceClient.document(query)
                .execute()
                .handle((ClientGraphQlResponse result, SynchronousSink<List<Map<String, Object>>> sink)
                        -> handleGraphQlResponse(result, sink, queryName))
                .retry(3)
                .block();

        if (meitrexStudentInfoList == null) {
            throw new UserServiceConnectionException("Error fetching userInfo from UserService");
        }

        return meitrexStudentInfoList;
    }

    private void handleGraphQlResponse(final ClientGraphQlResponse result, final SynchronousSink<List<Map<String, Object>>> sink, final String queryName) {
        if (!result.isValid()) {
            sink.error(new UserServiceConnectionException(result.getErrors().toString()));
            return;
        }

        List<Map<String, Object>> retrievedUserInfos = result.field(queryName).getValue();

        if (retrievedUserInfos == null) {
            sink.error(new UserServiceConnectionException("Error fetching userInfo from UserService: Missing field in response."));
            return;
        }
        if (retrievedUserInfos.isEmpty()) {
            sink.error(new UserServiceConnectionException("Error fetching userInfo from UserService: Field in response is empty."));
        }

        sink.next(retrievedUserInfos);
    }

    public List<String> saveStudentMappings(final UUID courseId, final List<StudentMappingInput> studentMappingInputs, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
        } catch (final NoAccessToCourseException ex) {
            return null;
        }

        // deletes all the external ids that have just been mapped from the manualMappingInstance-repo
        List<String> externalStudentIdList = studentMappingInputs.stream().map(StudentMappingInput::getExternalStudentId).toList();
        manualMappingInstanceRepository.deleteAllById(externalStudentIdList);

        // saves the new student mappings to the studentMapping-repo
        List<StudentMappingEntity> entityList = new ArrayList<>();
        for (final StudentMappingInput studentMappingInput : studentMappingInputs) {
            entityList.add(assignmentMapper.studentMappingInputToEntity(studentMappingInput));
        }
        studentMappingRepository.saveAll(entityList);

        // retries parsing all unfinishedGradingEntities
        final List<Map<String, Object>> meitrexStudentInfoList;
        try {
            meitrexStudentInfoList = getMeitrexStudentInfoList();
        } catch (UserServiceConnectionException e){
            throw new RuntimeException(e); // wrapping exception
            // return null; TODO return null or throw wrapped exception?
        }

        List<UnfinishedGradingEntity> unfinishedGradingEntityList = unfinishedGradingRepository.findAll();
        for (final UnfinishedGradingEntity unfinishedGradingEntity : unfinishedGradingEntityList) {
            JSONObject jsonObject = new JSONObject(unfinishedGradingEntity.getGradingJson());
            AssignmentEntity assignmentEntity = assignmentService.requireAssignmentExists(unfinishedGradingEntity.getId().getAssignmentId());

            try {
                GradingEntity gradingEntity = parseIntoGradingEntity(jsonObject, assignmentEntity, meitrexStudentInfoList);
                gradingRepository.save(gradingEntity);
                logGradingImported(gradingEntity);
                unfinishedGradingRepository.deleteById(unfinishedGradingEntity.getId());
            } catch (ManualMappingRequiredException | ExternalPlatformConnectionException e) {
                // if something goes wrong, unfinished gradings will be added to repo again
                unfinishedGradingEntity.incrementNumberOfTries();
                unfinishedGradingRepository.save(unfinishedGradingEntity);
            }

        }

        // returns all the newly mapped external student ids
        return externalStudentIdList;
    }

    public List<ManualMappingInstance> getManualMappingInstances(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
        } catch (final NoAccessToCourseException ex) {
            return null;
        }

        List<ManualMappingInstanceEntity> entityList = manualMappingInstanceRepository.findAll();

        return entityList.stream().map(assignmentMapper::manualMappingInstanceEntityToDto).toList();
    }

    /**
     * Gets external assignment information from TMS. <br>
     * This is needed for mapping MEITREX-Assignments to TMS-Assignments.
     *
     * @param courseId current course needed for role check
     * @param currentUser logged in user
     * @return list of ExternalAssignments, i.e. the external id and its sheet number
     */
    public List<ExternalAssignment> getExternalAssignments(final UUID courseId, final LoggedInUser currentUser) {
        try {
            validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, courseId);
        } catch (final NoAccessToCourseException ex) {
            return null;
        }

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


    /**
     * Parses external assignments into a list of ExternalAssignments containing an external id and sheet number.
     *
     * @param string JSON Array containing external sheets
     * @return list of ExternalAssignments, i.e. the external id and its sheet number
     */
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
