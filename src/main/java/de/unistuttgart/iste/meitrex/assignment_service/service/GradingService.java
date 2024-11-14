package de.unistuttgart.iste.meitrex.assignment_service.service;


import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.GradingEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.AssignmentMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.GradingRepository;
import de.unistuttgart.iste.meitrex.assignment_service.validation.AssignmentValidator;
import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import de.unistuttgart.iste.meitrex.common.exception.NoAccessToCourseException;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalAssignment;
import de.unistuttgart.iste.meitrex.generated.dto.Grading;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.json.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        List<ExternalAssignment> externalAssignments = this.parseStringIntoList(body);

        return externalAssignments;
    }

    private List<ExternalAssignment> parseStringIntoList(final String string) {

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
