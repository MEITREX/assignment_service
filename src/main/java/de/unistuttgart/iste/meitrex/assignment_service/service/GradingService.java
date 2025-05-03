package de.unistuttgart.iste.meitrex.assignment_service.service;


import de.unistuttgart.iste.meitrex.assignment_service.config.ExternalSystemConfiguration;
import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.exception.ManualMappingRequiredException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.CodeAssignmentMetadataEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.ExerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.exercise.SubexerciseEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.grading.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.*;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.ExternalGrading;
import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import de.unistuttgart.iste.meitrex.content_service.exception.ContentServiceConnectionException;
import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.course_service.exception.CourseServiceConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.*;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.json.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static de.unistuttgart.iste.meitrex.common.user_handling.UserCourseAccessValidator.validateUserHasAccessToCourse;

@Slf4j
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

    private final UserServiceClient userServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final ContentServiceClient contentServiceClient;

    private final ExternalSystemConfiguration externalSystemConfiguration;
    private final CodeAssessmentProvider codeAssessmentProvider;
    private final AssignmentRepository assignmentRepository;

    /**
     * Returns a grading for the given student on the given assignment.
     *
     * @param assignmentId meitrex id of the assignment
     * @param studentId meitrex id of the student
     * @param currentUser the current logged-in user
     * @return a grading for the student on the assignment, null if user has no access
     * @throws EntityNotFoundException if assignment with given id or grading doesn't exist
     */
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

    private List<Grading> getGradingForStudent(final AssignmentEntity assignment, final LoggedInUser currentUser){
        final GradingEntity.PrimaryKey pk = new GradingEntity.PrimaryKey(assignment.getId(), currentUser.getId());
        GradingEntity gradingEntity = gradingRepository.findById(pk).orElse(null);

        if (gradingEntity == null && assignment.getAssignmentType() == AssignmentType.CODE_ASSIGNMENT) {
            gradingEntity = GradingEntity.builder()
                    .primaryKey(pk)
                    .achievedCredits(-1)
                    .build();

            CodeAssignmentGradingMetadataEntity metadata = CodeAssignmentGradingMetadataEntity.builder()
                    .grading(gradingEntity)
                    .repoLink(null)
                    .status(null)
                    .feedbackTableHtml(null)
                    .build();

            gradingEntity.setCodeAssignmentGradingMetadata(metadata);
        }

        if (gradingEntity != null && gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink() == null && assignment.getAssignmentType() == AssignmentType.CODE_ASSIGNMENT) {
            try {
                String assignmentName = contentServiceClient.queryContentsOfCourse(currentUser.getId(), assignment.getCourseId()).stream()
                        .filter(assignmentDto -> assignmentDto.getId().equals(assignment.getId()))
                        .findFirst()
                        .orElseThrow(() -> new EntityNotFoundException("Assignment with externalId %s not found".formatted(assignment.getExternalId())))
                        .getMetadata().getName();

                String repoLink = codeAssessmentProvider.findRepository(assignmentName, currentUser);
                gradingEntity.getCodeAssignmentGradingMetadata().setRepoLink(repoLink);

            } catch (ExternalPlatformConnectionException | UserServiceConnectionException | ContentServiceConnectionException e) {
                throw new RuntimeException(e.toString());
            }
        }

        if (gradingEntity != null && gradingEntity.getCodeAssignmentGradingMetadata() != null &&
                gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink() != null) {
            try {
                ExternalGrading externalGrading = codeAssessmentProvider.syncGradeForStudent(gradingEntity.getCodeAssignmentGradingMetadata().getRepoLink(), currentUser);
                gradingEntity.setAchievedCredits(externalGrading.achievedPoints());
                CodeAssignmentGradingMetadataEntity metadata = gradingEntity.getCodeAssignmentGradingMetadata();
                metadata.setStatus(externalGrading.status());
                metadata.setFeedbackTableHtml(externalGrading.tableHtml());
                gradingEntity.setDate(OffsetDateTime.parse(externalGrading.date()));
            } catch (ExternalPlatformConnectionException | UserServiceConnectionException e) {
                log.error("Failed to sync student grade for assignment {} and student {}: {}", assignment.getId(), currentUser.getId(), e.toString());
            }
        }

        gradingEntity = gradingRepository.save(gradingEntity);
        return List.of(assignmentMapper.gradingEntityToDto(gradingEntity));
    }

    public List<Grading> getGradingsForAssignment(final UUID assignmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignment = assignmentService.requireAssignmentExists(assignmentId);

        LoggedInUser.CourseMembership courseMembership = (LoggedInUser.CourseMembership) currentUser.getCourseMemberships().stream().filter((membership) -> membership.getCourseId().equals(assignment.getCourseId())).findFirst().orElseThrow(() -> new NoAccessToCourseException(assignment.getCourseId(), "User is not a member of the course."));

        if (courseMembership.getRole() == LoggedInUser.UserRoleInCourse.STUDENT) {
           return getGradingForStudent(assignment, currentUser);
        }

        return Collections.emptyList();
//
//        List<ExternalGrading> externalGrades;
//        try {
//            externalGrades = codeAssessmentProvider.syncGrades(assignment.getExternalId(), currentUser);
//        } catch (ExternalPlatformConnectionException | UserServiceConnectionException e) {
//            throw new RuntimeException("Failed to sync grades from GitHub Classroom" + e);
//        }
//
//        if (assignment.getTotalCredits() == -1 && !externalGrades.isEmpty()) {
//            assignment.setTotalCredits(externalGrades.get(0).totalPoints());
//            assignmentRepository.save(assignment);
//        }
//
//        final List<UUID> studentIds;
//        try {
//            studentIds = getMeitrexStudentInfoList(assignment.getCourseId()).stream()
//                    .map(UserInfo::getId)
//                    .collect(Collectors.toList());
//        } catch (UserServiceConnectionException | CourseServiceConnectionException e) {
//            throw new RuntimeException("Failed to retrieve student info for course.", e);
//        }
//
//        List<ExternalUserIdWithUser> externalIds;
//        try {
//            externalIds = userServiceClient.queryExternalUserIds(ExternalServiceProviderDto.GITHUB, studentIds);
//        } catch (UserServiceConnectionException e) {
//            throw new RuntimeException("Failed to retrieve external user IDs from UserService.", e);
//        }
//
//        Map<String, UUID> githubToStudentId = externalIds.stream()
//                .collect(Collectors.toMap(ExternalUserIdWithUser::getExternalUserId, ExternalUserIdWithUser::getUserId));
//
//        Map<UUID, GradingEntity> existing = gradingRepository.findAllByPrimaryKey_AssessmentId(assignmentId).stream()
//                .collect(Collectors.toMap(
//                        grading -> grading.getPrimaryKey().getStudentId(),
//                        grading -> grading
//                ));
//
//        List<GradingEntity> updatedGradings = new ArrayList<>();
//        for (ExternalGrading external : externalGrades) {
//            UUID studentId = githubToStudentId.get(external.externalUsername());
//            if (studentId != null) {
//                GradingEntity grading = existing.get(studentId);
//
//                //grading not null since if an external grade is found, then a student accepted the assignment and created a grading with -1 points
//                grading.setAchievedCredits(external.achievedPoints());
//
//                updatedGradings.add(grading);
//            }
//        }
//
//
//        gradingRepository.saveAll(updatedGradings);
//        return gradingRepository.findAllByPrimaryKey_AssessmentId(assignmentId).stream()
//                .map(assignmentMapper::gradingEntityToDto)
//                .collect(Collectors.toList());
    }



    /**
     * Handles importing all gradings for one assignment from the external system (TMS).
     * <br><br>
     * All gradings for one assignment are requested from the external system (TMS) via HTTP. The response is parsed to gradingEntities. <br>
     * These gradingEntities are saved in the grading repository. <br>
     * A contentProgressedEvent is published for each grading (i.e. each student).
     *
     * @param assignmentId id of the assignment of which the gradings should be imported
     * @param currentUser the user requesting the import (needs to be admin)
     */
    public void importGradingsForAssignment(final UUID assignmentId, final LoggedInUser currentUser) {
        final AssignmentEntity assignment = assignmentService.requireAssignmentExists(assignmentId); // throws EntityNotFoundException "Assignment with assessmentId %s not found"
        validateUserHasAccessToCourse(currentUser, LoggedInUser.UserRoleInCourse.ADMINISTRATOR, assignment.getCourseId());

        final List<UserInfo> meitrexStudentInfoList;
        try {
            meitrexStudentInfoList = getMeitrexStudentInfoList(assignment.getCourseId());
        } catch (UserServiceConnectionException | CourseServiceConnectionException e){
            throw new RuntimeException(e); // wrapping exception
            // return; TODO return or throw wrapped exception?
        }

        String externalId = assignment.getExternalId();

        String body;
        CompletableFuture<String> response;
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(externalSystemConfiguration.getBasePath() + "api/grading/handIn/" + externalId))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + externalSystemConfiguration.getAuthToken())
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
     * @param meitrexStudentInfoList list of all user infos, passed down for matching ids
     * @return List of parsed grading entities
     */
    private List<GradingEntity> parseStringIntoGradingEntityList(final String string, final AssignmentEntity assignmentEntity, final List<UserInfo> meitrexStudentInfoList) {
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
     * <br>
     * If the grading can't be imported because ids can't be mapped or the connection to the external system failed,
     * the grading will be skipped and tried again later.
     *
     * @param jsonObject JSON Object containing a single TMS-grading
     * @param assignmentEntity the assignment id which the grading belongs to
     * @param meitrexStudentInfoList list of all user infos, passed down for matching ids
     * @return parsed grading entity
     */
    private GradingEntity parseIntoGradingEntity(final JSONObject jsonObject, final AssignmentEntity assignmentEntity, final List<UserInfo> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
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


    /**
     * If a grading could not be imported (e.g. because the corresponding meitrex user could not be found),
     * it is added to the unfinished grading repository, so that it can be tried again later. <br>
     * Also increments the number of tries, if the grading has been tried before.
     *
     * @param jsonObject JSON Object representing a grading (the way it was received from the external system)
     * @param assignmentEntity meitrex assignment entity for which the grading is
     * @param externalStudentId external student id for whom the grading is
     */
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

    /**
     * Tries to get the corresponding meitrex student id for a given external student id. <br>
     * Case 1: A student mapping is found: the id is returned. <br>
     * Case 2: No student mapping is found: tries to find a new matching id for the external id: <br>
     * - finds new id: meitrex id is found <br>
     * - can't find new id: throws ManualMappingRequiredException, so that an admin will map <br>
     *
     * @param externalStudentId external id of the student
     * @param meitrexStudentInfoList list of all user infos
     * @return meitrex id if it is found
     * @throws ManualMappingRequiredException if no matching id could be found
     * @throws ExternalPlatformConnectionException if connection to the external system failed
     */
    private UUID getStudentIdFromExternalStudentId(final String externalStudentId, final List<UserInfo> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
        Optional<StudentMappingEntity> studentMappingEntity = studentMappingRepository.findById(externalStudentId);
        if (studentMappingEntity.isPresent()) {
            return studentMappingEntity.get().getMeitrexStudentId();
        }
        UUID newMeitrexStudentId = findNewStudentIdFromExternalStudentId(externalStudentId, meitrexStudentInfoList); // throws exception if nothing is found
        studentMappingRepository.save(new StudentMappingEntity(externalStudentId, newMeitrexStudentId));
        return newMeitrexStudentId;
    }

    /**
     * Tries to match a meitrex user to the given external student. <br>
     * Gets all infos on external student and filters meitrexStudentInfoList for these properties. <br>
     * - last name <br>
     * - first name <br>
     * - future: email, ... <br>
     *
     * If no unique match is found, a ManualMappingRequiredException is thrown, so that an admin will map.
     *
     * @param externalStudentId external id of the student
     * @param meitrexStudentInfoList list of all user infos in meitrex
     * @return meitrex id of the student
     * @throws ManualMappingRequiredException if no or multiple matching students could be found
     * @throws ExternalPlatformConnectionException if connection to the external system fails
     */
    private UUID findNewStudentIdFromExternalStudentId(final String externalStudentId, final List<UserInfo> meitrexStudentInfoList) throws ManualMappingRequiredException, ExternalPlatformConnectionException {
        JSONObject externalStudentInfo = getExternalStudentInfo(externalStudentId);

        // list is fetched from user service at the beginning, rather than for each grading
        // final List<UserInfo> meitrexStudentInfoList = getMeitrexStudentInfoList();

        Object lastName = externalStudentInfo.get("lastname");
        Object firstName = externalStudentInfo.get("firstname");

        // filter by last name
        List<UserInfo> filteredByLastName = meitrexStudentInfoList.stream()
                .filter(userInfo -> userInfo.getLastName().equals(lastName))
                .toList();
        if (filteredByLastName.isEmpty()) {
            throw new ManualMappingRequiredException(externalStudentInfo);
        } else if (filteredByLastName.size() == 1) {
            return (UUID) filteredByLastName.getFirst().getId();
        }

        // filter by first name, if there are still multiple candidates
        List<UserInfo> filteredByFirstName = filteredByLastName.stream()
                .filter(userInfo -> userInfo.getFirstName().equals(firstName))
                .toList();
        if (filteredByFirstName.isEmpty()) {
            throw new ManualMappingRequiredException(externalStudentInfo);
        } else if (filteredByFirstName.size() == 1) {
            return (UUID) filteredByFirstName.getFirst().getId();
        }

        // filter by more attributes like email, matriculation number etc. if there are still more candidates

        // if no match is found, the id needs to be mapped manually
        throw new ManualMappingRequiredException(externalStudentInfo);
    }

    /**
     * Gets all infos on student from external system. <br>
     * Contains at least: last name, first name <br>
     * Optional: email, matriculation number
     *
     * @param externalStudentId external id of the student
     * @return JSON Object containing all available information
     * @throws ExternalPlatformConnectionException if connection to external system fails
     */
    private JSONObject getExternalStudentInfo(final String externalStudentId) throws ExternalPlatformConnectionException {
        String body;
        CompletableFuture<String> response;
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(externalSystemConfiguration.getBasePath() + "api/student/" + externalStudentId))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + externalSystemConfiguration.getAuthToken())
                    .build();
            response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
            body = response.join();
        }

        if (body == null) throw new ExternalPlatformConnectionException("Querying external student info for externalStudentId %s went wrong.".formatted(externalStudentId));

        return new JSONObject(body);
    }

    /**
     * Gets all user infos on all users in the course of the assignment. <br>
     * First gets all ids in the course. Then gets all user infos on these ids.
     *
     * @return list of user infos on all users in the course
     * @throws CourseServiceConnectionException if something went wrong querying user ids in the course
     * @throws UserServiceConnectionException if something went wrong querying user infos
     */
    private List<UserInfo> getMeitrexStudentInfoList(final UUID courseId) throws CourseServiceConnectionException, UserServiceConnectionException {

        final List<CourseMembership> userMemberships = courseServiceClient.queryMembershipsInCourse(courseId);
        List<UUID> userIds = userMemberships.stream().map(CourseMembership::getUserId).toList();
        final List<UserInfo> meitrexStudentInfoList = userServiceClient.queryUserInfos(userIds);

        return meitrexStudentInfoList;
    }


    /**
     * Saves all manually created student mappings to the repository.
     * Then tries parsing unfinished gradings for which the assignment is in the given course.
     *
     * @param courseId id of the course the mappings are in
     * @param studentMappingInputs inputs for student mappings
     * @param currentUser current logged-in user
     * @return returns all the newly mapped external student ids (without purpose)
     */
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

        // first only the ones in the course are tried
        // mainly because it's faster
        // but also because an admin might be able to match more students in the current course
        retryUnfinishedGradingsInCourse(courseId);
        // then all remaining
        retryAllUnfinishedGradings();

        // returns all the newly mapped external student ids (with no real purpose, only because graphql doesn't allow void)
        return externalStudentIdList;
    }

    /**
     * Retries parsing unfinishedGradingEntities in the given course
     * @param courseId the current course
     */
    private void retryUnfinishedGradingsInCourse(UUID courseId) {
        // retries parsing unfinishedGradingEntities in the current course
        final List<UserInfo> meitrexStudentInfoList;
        try {
            meitrexStudentInfoList = getMeitrexStudentInfoList(courseId);
        } catch (UserServiceConnectionException | CourseServiceConnectionException e){
            // throw new RuntimeException(e); // wrapping exception
            return; // TODO return or throw wrapped exception?
        }

        List<UnfinishedGradingEntity> unfinishedGradingEntityList = unfinishedGradingRepository.findAll();
        for (final UnfinishedGradingEntity unfinishedGradingEntity : unfinishedGradingEntityList) {
            JSONObject jsonObject = new JSONObject(unfinishedGradingEntity.getGradingJson());
            AssignmentEntity assignmentEntity = assignmentService.requireAssignmentExists(unfinishedGradingEntity.getId().getAssignmentId());

            if (assignmentEntity.getCourseId().equals(courseId)) {
                try {
                    // throws the caught exceptions
                    GradingEntity gradingEntity = parseIntoGradingEntity(jsonObject, assignmentEntity, meitrexStudentInfoList);
                    // should not throw anything
                    gradingRepository.save(gradingEntity);
                    logGradingImported(gradingEntity);
                    unfinishedGradingRepository.deleteById(unfinishedGradingEntity.getId());
                } catch (ManualMappingRequiredException | ExternalPlatformConnectionException e){
                    // if something goes wrong, unfinished gradings will be added to repo again
                    unfinishedGradingEntity.incrementNumberOfTries();
                    unfinishedGradingRepository.save(unfinishedGradingEntity);
                }
            }
        }
    }


    /**
     * Retries parsing all unfinished gradings.
     * Might be slow because user infos need to be queried for every grading.
     */
    private void retryAllUnfinishedGradings() {
        List<UserInfo> meitrexStudentInfoList;

        List<UnfinishedGradingEntity> unfinishedGradingEntityList = unfinishedGradingRepository.findAll();

        for (final UnfinishedGradingEntity unfinishedGradingEntity : unfinishedGradingEntityList) {
            JSONObject jsonObject = new JSONObject(unfinishedGradingEntity.getGradingJson());
            AssignmentEntity assignmentEntity = assignmentService.requireAssignmentExists(unfinishedGradingEntity.getId().getAssignmentId());

            try {
                // throws first two exceptions
                meitrexStudentInfoList = getMeitrexStudentInfoList(assignmentEntity.getCourseId());
                // throws second two exceptions
                GradingEntity gradingEntity = parseIntoGradingEntity(jsonObject, assignmentEntity, meitrexStudentInfoList);
                // should not throw anything
                gradingRepository.save(gradingEntity);
                logGradingImported(gradingEntity);
                unfinishedGradingRepository.deleteById(unfinishedGradingEntity.getId());
            } catch (UserServiceConnectionException | CourseServiceConnectionException |
                     ManualMappingRequiredException | ExternalPlatformConnectionException e){
                // if something goes wrong, unfinished gradings will be added to repo again
                unfinishedGradingEntity.incrementNumberOfTries();
                unfinishedGradingRepository.save(unfinishedGradingEntity);
            }
        }
    }

    /**
     * Returns all instances, where an admin needs to manually map a meitrex user to an external student.
     *
     * @param courseId the current course
     * @param currentUser the currently logged-in user
     * @return list of all manual mapping instances
     */
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
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(externalSystemConfiguration.getBasePath() + "api/sheet"))
                    //.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .header("Cookie", "connect.sid=" + externalSystemConfiguration.getAuthToken())
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
