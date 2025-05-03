package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import com.google.gson.*;
import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.AssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.AssignmentType;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalServiceProviderDto;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.AccessToken;

import jakarta.persistence.Access;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.expression.spel.ast.Assign;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@Primary
public class GithubClassroom implements CodeAssessmentProvider {

    private final ExternalServiceProviderDto NAME = ExternalServiceProviderDto.GITHUB;
    private final String BASE_PATH = "https://api.github.com";
    private final String VERSION = "2022-11-28";
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final UserServiceClient userServiceClient;
    private final AssignmentRepository assignmentRepository;
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository;
    private final String organizationName;

    public GithubClassroom(UserServiceClient userServiceClient, AssignmentRepository assignmentRepository,
                           ExternalCodeAssignmentRepository externalCodeAssignmentRepository,
                           @Value("${github.organization_name}") String organizationName) {
        this.userServiceClient = userServiceClient;
        this.assignmentRepository = assignmentRepository;
        this.externalCodeAssignmentRepository = externalCodeAssignmentRepository;
        this.organizationName = organizationName;
    }

    @Override
    public void syncAssignmentsForCourse(String courseTitle, LoggedInUser currentUser) throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();

            HttpRequest classroomsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_PATH + "/classrooms"))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", VERSION)
                    .GET()
                    .build();

            HttpResponse<String> classroomsResponse = client.send(classroomsRequest, HttpResponse.BodyHandlers.ofString());
            if (classroomsResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch classrooms: " + classroomsResponse.body());
            }

            JsonArray classrooms = JsonParser.parseString(classroomsResponse.body()).getAsJsonArray();
            JsonObject classroom = findByNameIgnoreCase(classrooms, "name", courseTitle);
            int classroomId = classroom.get("id").getAsInt();

            HttpRequest assignmentsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_PATH + "/classrooms/" + classroomId + "/assignments"))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", VERSION)
                    .GET()
                    .build();

            HttpResponse<String> assignmentsResponse = client.send(assignmentsRequest, HttpResponse.BodyHandlers.ofString());
            if (assignmentsResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch assignments: " + assignmentsResponse.body());
            }

            JsonArray assignments = JsonParser.parseString(assignmentsResponse.body()).getAsJsonArray();
            List<String> fetchedAssignmentNames = new ArrayList<>();

            for (JsonElement assignmentElem : assignments) {
                JsonObject assignment = assignmentElem.getAsJsonObject();

                String assignmentId = assignment.get("id").getAsString();
                String assignmentName = assignment.get("title").getAsString();
                fetchedAssignmentNames.add(assignmentName);
                String assignmentLink = classroom.get("url").getAsString() + "/assignments/" + assignment.get("slug").getAsString();
                String invitationLink = assignment.get("invite_link").getAsString();
                String slug = assignment.get("slug").getAsString();
                JsonElement deadlineElement = assignment.get("deadline");
                OffsetDateTime dueDate = null;
                if (deadlineElement != null && !deadlineElement.isJsonNull()) {
                    dueDate = OffsetDateTime.parse(deadlineElement.getAsString());
                }

                // Fetch README
                String readmeHtml = "";
                try {
                    HttpRequest assignmentDetailsRequest = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_PATH + "/assignments/" + assignmentId))
                            .header("Accept", "application/vnd.github+json")
                            .header("Authorization", "Bearer " + token)
                            .header("X-GitHub-Api-Version", VERSION)
                            .GET()
                            .build();

                    HttpResponse<String> assignmentDetailsResponse = client.send(assignmentDetailsRequest, HttpResponse.BodyHandlers.ofString());
                    if (assignmentDetailsResponse.statusCode() != 200) {
                        throw new ExternalPlatformConnectionException("Failed to fetch assignment details: " + assignmentDetailsResponse.body());
                    }

                    JsonObject detailedAssignment = JsonParser.parseString(assignmentDetailsResponse.body()).getAsJsonObject();
                    String fullName = null;
                    JsonElement repoElement = detailedAssignment.get("starter_code_repository");

                    if (repoElement != null && !repoElement.isJsonNull()) {
                        fullName = repoElement.getAsJsonObject().get("full_name").getAsString();
                    }

                    HttpRequest readmeRequest = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_PATH + "/repos/" + fullName + "/readme"))
                            .header("Accept", "application/vnd.github.html+json")
                            .header("Authorization", "Bearer " + token)
                            .header("X-GitHub-Api-Version", VERSION)
                            .GET()
                            .build();

                    HttpResponse<String> readmeResponse = client.send(readmeRequest, HttpResponse.BodyHandlers.ofString());
                    if (readmeResponse.statusCode() == 200) {
                        String rawReadmeHtml = readmeResponse.body();
                        // Clean GitHub README HTML by removing visual noise (e.g., anchor icons)
                        readmeHtml = rawReadmeHtml
                                // Remove full <a class="anchor">...</a> blocks including embedded SVG link icons
                                .replaceAll("<a[^>]*class=\"anchor\"[^>]*>\\s*<svg[^>]*>.*?</svg>\\s*</a>", "")
                                // Remove empty lines left behind after tag removal
                                .replaceAll("(?m)^\\s+$", "");
                    }
                } catch (IOException | InterruptedException e) {
                    // Ignore README failures
                }

                boolean codeAssignmentAlreadyExists = assignmentRepository.existsByExternalId(assignmentId);

                if (codeAssignmentAlreadyExists) {
                    log.info("Assignment '{}' already exists. Skipping sync.", assignmentName);
                    continue;
                }

                externalCodeAssignmentRepository.save(ExternalCodeAssignmentEntity.builder()
                        .primaryKey(new ExternalCodeAssignmentEntity.PrimaryKey(courseTitle, assignmentName))
                        .externalId(assignmentId)
                        .assignmentLink(assignmentLink)
                        .invitationLink(invitationLink)
                        .dueDate(dueDate)
                        .readmeHtml(readmeHtml)
                        .build());
            }

            List<String> assignmentNamesByCourseTitle = externalCodeAssignmentRepository.findAssignmentNamesByCourseTitle(courseTitle);
            for (String assignmentName : assignmentNamesByCourseTitle) {
                if (!fetchedAssignmentNames.contains(assignmentName)) {
                    ExternalCodeAssignmentEntity.PrimaryKey pk = new ExternalCodeAssignmentEntity.PrimaryKey(courseTitle, assignmentName);
                    externalCodeAssignmentRepository.deleteById(pk);
                }
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch data from GitHub Classroom: " + e.getMessage());
        } catch (UserServiceConnectionException e) {
            throw new UserServiceConnectionException(e.getMessage());
        }
    }

//
//    @Override
//    public CodeAssignment findAssignment(LoggedInUser currentUser, String courseTitle, String assignmentName) throws ExternalPlatformConnectionException, UserServiceConnectionException {
//        try {
//            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
//            String token = queryTokenResponse.getAccessToken();
//
//            try {
//                HttpRequest classroomsRequest = HttpRequest.newBuilder()
//                        .uri(URI.create(BASE_PATH + "/classrooms"))
//                        .header("Accept", "application/vnd.github+json")
//                        .header("Authorization", "Bearer " + token)
//                        .header("X-GitHub-Api-Version", VERSION)
//                        .GET()
//                        .build();
//
//                HttpResponse<String> classroomsResponse = client.send(classroomsRequest, HttpResponse.BodyHandlers.ofString());
//                if (classroomsResponse.statusCode() != 200) {
//                    throw new ExternalPlatformConnectionException("Failed to fetch classrooms: " + classroomsResponse.body());
//                }
//
//                JsonArray classrooms = JsonParser.parseString(classroomsResponse.body()).getAsJsonArray();
//                JsonObject classroom = findByNameIgnoreCase(classrooms, "name", courseTitle);
//                int classroomId = classroom.get("id").getAsInt();
//
//                HttpRequest assignmentsRequest = HttpRequest.newBuilder()
//                        .uri(URI.create(BASE_PATH + "/classrooms/" + classroomId + "/assignments"))
//                        .header("Accept", "application/vnd.github+json")
//                        .header("Authorization", "Bearer " + token)
//                        .header("X-GitHub-Api-Version", VERSION)
//                        .GET()
//                        .build();
//
//                HttpResponse<String> assignmentsResponse = client.send(assignmentsRequest, HttpResponse.BodyHandlers.ofString());
//                if (assignmentsResponse.statusCode() != 200) {
//                    throw new ExternalPlatformConnectionException("Failed to fetch assignments: " + assignmentsResponse.body());
//                }
//
//                JsonArray assignments = JsonParser.parseString(assignmentsResponse.body()).getAsJsonArray();
//                JsonObject assignment = findByNameIgnoreCase(assignments, "title", assignmentName);
//
//                String assignmentLink = classroom.get("url").getAsString() + "/assignments/" + assignment.get("slug").getAsString();
//                String invitationLink = assignment.get("invite_link").getAsString();
//                JsonElement deadlineElement = assignment.get("deadline");
//                OffsetDateTime dueDate = null;
//                if (deadlineElement != null && !deadlineElement.isJsonNull()) {
//                    dueDate = OffsetDateTime.parse(deadlineElement.getAsString());
//                }
//                String assignmentId = assignment.get("id").getAsString();
//
//                HttpRequest assignmentDetailsRequest = HttpRequest.newBuilder()
//                        .uri(URI.create(BASE_PATH + "/assignments/" + assignmentId))
//                        .header("Accept", "application/vnd.github+json")
//                        .header("Authorization", "Bearer " + token)
//                        .header("X-GitHub-Api-Version", VERSION)
//                        .GET()
//                        .build();
//
//                HttpResponse<String> assignmentDetailsResponse = client.send(assignmentDetailsRequest, HttpResponse.BodyHandlers.ofString());
//                if (assignmentDetailsResponse.statusCode() != 200) {
//                    throw new ExternalPlatformConnectionException("Failed to fetch assignment details: " + assignmentDetailsResponse.body());
//                }
//
//                JsonObject detailedAssignment = JsonParser.parseString(assignmentDetailsResponse.body()).getAsJsonObject();
//                String fullName = detailedAssignment.getAsJsonObject("starter_code_repository").get("full_name").getAsString();
//
//                HttpRequest readmeRequest = HttpRequest.newBuilder()
//                        .uri(URI.create(BASE_PATH + "/repos/" + fullName + "/readme"))
//                        .header("Accept", "application/vnd.github.html+json")
//                        .header("Authorization", "Bearer " + token)
//                        .header("X-GitHub-Api-Version", VERSION)
//                        .GET()
//                        .build();
//
//                HttpResponse<String> readmeResponse = client.send(readmeRequest, HttpResponse.BodyHandlers.ofString());
//                String readmeHtml = null;
//                if (readmeResponse.statusCode() == 200) {
//                    String rawReadmeHtml = readmeResponse.body();
//                    // Clean GitHub README HTML by removing visual noise (e.g., anchor icons)
//                    readmeHtml = rawReadmeHtml
//                            // Remove full <a class="anchor">...</a> blocks including embedded SVG link icons
//                            .replaceAll("<a[^>]*class=\"anchor\"[^>]*>\\s*<svg[^>]*>.*?</svg>\\s*</a>", "")
//                            // Remove empty lines left behind after tag removal
//                            .replaceAll("(?m)^\\s+$", "");
//                }
//
//                return new CodeAssignment(assignmentId, assignmentLink, invitationLink, dueDate, readmeHtml);
//            } catch (IOException | InterruptedException | IllegalStateException e) {
//                throw new ExternalPlatformConnectionException("Failed to fetch data from GitHub Classroom: " + e.getMessage());
//            }
//        } catch (UserServiceConnectionException e) {
//            throw new UserServiceConnectionException(e.getMessage());
//        }
//    }


    private JsonObject findByNameIgnoreCase(JsonArray array, String fieldName, String targetName) {
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has(fieldName) && obj.get(fieldName).getAsString().equalsIgnoreCase(targetName)) {
                return obj;
            }
        }
        throw new IllegalStateException("No element found with " + fieldName + " matching: " + targetName);
    }

    @Override
    public List<ExternalGrading> syncGrades(final String externalAssignmentId, final LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_PATH + "/assignments/" + externalAssignmentId + "/grades"))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", VERSION)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch grades: " + response.body());
            }

            JsonArray gradesArray = JsonParser.parseString(response.body()).getAsJsonArray();
            List<ExternalGrading> gradings = new ArrayList<>();

            for (JsonElement element : gradesArray) {
                JsonObject obj = element.getAsJsonObject();
                String username = obj.get("github_username").getAsString();
                double achieved = obj.get("points_awarded").getAsDouble();
                double total = obj.get("points_available").getAsDouble();
                gradings.add(new ExternalGrading(username, null, null, null, achieved, total));
            }

            return gradings;
        } catch (IOException | InterruptedException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch grades: " + e.getMessage());
        } catch (UserServiceConnectionException e) {
            throw new UserServiceConnectionException(e.getMessage());
        }
    }

    public ExternalGrading syncGradeForStudent(String repoLink, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {

        try {
            AccessToken tokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = tokenResponse.getAccessToken();

            // Extract owner/repo from repoLink
            URI uri = URI.create(repoLink);
            String[] parts = uri.getPath().split("/");
            if (parts.length < 3) {
                throw new ExternalPlatformConnectionException("Invalid repo URL: " + repoLink);
            }
            String owner = parts[1];
            String repo = parts[2];

            // Get latest completed workflow runs
            HttpRequest runsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_PATH + "/repos/" + owner + "/" + repo + "/actions/runs?status=completed"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", VERSION)
                    .GET()
                    .build();

            HttpResponse<String> runsResponse = client.send(runsRequest, HttpResponse.BodyHandlers.ofString());
            if (runsResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch workflow runs: " + runsResponse.body());
            }

            JsonArray runs = JsonParser.parseString(runsResponse.body())
                    .getAsJsonObject().getAsJsonArray("workflow_runs");
            if (runs.size() == 0) {
                throw new ExternalPlatformConnectionException("No completed workflow runs found.");
            }

            JsonObject run = runs.get(0).getAsJsonObject();

            String status = run.get("status").getAsString();
            String logsUrl = run.get("logs_url").getAsString();
            String lastlyTested = run.get("updated_at").getAsString();

            // Download logs
            HttpRequest logRequest = HttpRequest.newBuilder()
                    .uri(URI.create(logsUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", VERSION)
                    .GET()
                    .build();

            HttpResponse<byte[]> logResponse = client.send(logRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (logResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch workflow logs: " + logResponse.body());
            }

            byte[] zipBytes = logResponse.body();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes);
                 ZipInputStream zis = new ZipInputStream(bais)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().contains("run-autograding-tests.txt")) {
                        StringBuilder logBuilder = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logBuilder.append(line).append("\n");
                        }
                        String logs = logBuilder.toString();

                        // Now apply your regex on `logs`
                        Pattern pattern = Pattern.compile("\\{\"totalPoints\":(\\d+(?:\\.\\d+)?),\"maxPoints\":(\\d+(?:\\.\\d+)?)\\}");
                        Matcher matcher = pattern.matcher(logs);
                        if (matcher.find()) {
                            double totalPoints = Double.parseDouble(matcher.group(1));
                            double maxPoints = Double.parseDouble(matcher.group(2));
                            String tableHtml = extractGradingTableAsHtml(logs);
                            return new ExternalGrading(null, status, lastlyTested, tableHtml, totalPoints, maxPoints);
                        } else {
                            throw new ExternalPlatformConnectionException("Could not find totalPoints/maxPoints in logs.");
                        }
                    }
                }

                throw new ExternalPlatformConnectionException("No grading file found in logs.");
            }
        } catch (IOException | InterruptedException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch student grade: " + e.getMessage());
        }
    }

    private String extractGradingTableAsHtml(String logs) {
        // Remove ANSI escape sequences
        logs = logs.replaceAll("\u001B\\[[;\\d]*m", "");

        String[] lines = logs.split("\n");
        StringBuilder tableHtml = new StringBuilder("<table border=\"1\">\n");

        // Step 1: Map short runner keys to full test names (for ✅ and ❌)
        Map<String, String> runnerNameToTitle = new HashMap<>();
        Pattern processingPattern = Pattern.compile("Processing: ([\\w\\-]+)");
        Pattern titlePattern = Pattern.compile("[✅❌] ([A-Z].+)");
        String lastRunner = null;

        for (String line : lines) {
            Matcher m1 = processingPattern.matcher(line);
            Matcher m2 = titlePattern.matcher(line);
            if (m1.find()) {
                lastRunner = m1.group(1).trim();
            } else if (m2.find() && lastRunner != null) {
                runnerNameToTitle.put(lastRunner, m2.group(1).trim());
                lastRunner = null;
            }
        }

        // Step 2: Extract and rewrite the grading table
        boolean inTable = false;
        for (String line : lines) {
            line = line.trim();
            if (line.contains("Test runner summary")) {
                inTable = true;
                continue;
            }
            if (!inTable) continue;

            if (line.startsWith("┌") || line.startsWith("├") || line.startsWith("└") || line.isEmpty()) {
                continue; // Skip border lines and empty lines
            }

            String[] cells = line.split("│");
            if (cells.length < 3) continue;

            if (tableHtml.indexOf("<thead>") == -1) {
                // Header
                tableHtml.append("<thead><tr>");
                for (int i = 1; i < cells.length - 1; i++) {
                    tableHtml.append("<th>").append(cells[i].trim()).append("</th>");
                }
                tableHtml.append("</tr></thead>\n<tbody>\n");
            } else {
                if (cells[1].trim().equalsIgnoreCase("Total:")) {
                    break;
                }

                String rawName = cells[1].trim();
                String score = cells[2].trim();

                // Try to map to full test name
                String resolvedName = rawName;
                for (String key : runnerNameToTitle.keySet()) {
                    if (rawName.startsWith(key.substring(0, Math.min(key.length(), 10)))) {
                        resolvedName = runnerNameToTitle.get(key);
                        break;
                    }
                }

                tableHtml.append("<tr><td>").append(resolvedName).append("</td><td style=\"text-align:center;\">").append(score).append("</td></tr>\n");
            }
        }

        tableHtml.append("</tbody></table>");
        return tableHtml.toString();
    }




    @Override
    public String findRepository(final String assignmentName, final LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();
            String githubUsername = queryTokenResponse.getExternalUserId();

            String slug = assignmentName.toLowerCase().replaceAll("\\s+", "-");
            String repoName = slug + "-" + githubUsername;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_PATH + "/repos/" + organizationName + "/" + repoName))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch repository: " + response.body());
            }

            JsonObject repo = JsonParser.parseString(response.body()).getAsJsonObject();
            return repo.has("html_url") ? repo.get("html_url").getAsString() : null;

        } catch (IOException | InterruptedException e) {
            throw new ExternalPlatformConnectionException("Error fetching student repo link: " + e.getMessage());
        }
    }

//    @Override
//    public String findRepository(final AssignmentEntity assignment, final LoggedInUser currentUser)
//            throws ExternalPlatformConnectionException, UserServiceConnectionException {
//
//        try {
//            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
//            String token = queryTokenResponse.getAccessToken();
//            String githubUsername = queryTokenResponse.getExternalUserId();
//
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(BASE_PATH + "/assignments/" + assignment.getExternalId() + "/accepted_assignments"))
//                    .header("Accept", "application/vnd.github+json")
//                    .header("Authorization", "Bearer " + token)
//                    .header("X-GitHub-Api-Version", VERSION)
//                    .GET()
//                    .build();
//
//            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//            if (response.statusCode() != 200) {
//                throw new ExternalPlatformConnectionException("Failed to fetch accepted assignments: " + response.body());
//            }
//
//            JsonArray acceptedArray = JsonParser.parseString(response.body()).getAsJsonArray();
//
//            for (JsonElement element : acceptedArray) {
//                JsonObject obj = element.getAsJsonObject();
//                JsonArray students = obj.getAsJsonArray("students");
//
//                for (JsonElement studentElement : students) {
//                    JsonObject student = studentElement.getAsJsonObject();
//                    if (student.get("login").getAsString().equalsIgnoreCase(githubUsername)) {
//                        JsonObject repository = obj.getAsJsonObject("repository");
//                        if (repository != null && repository.has("html_url")) {
//                            return repository.get("html_url").getAsString();
//                        }
//                    }
//                }
//            }
//
//            return null;
//
//        } catch (IOException | InterruptedException e) {
//            throw new ExternalPlatformConnectionException("Error fetching student repo link: " + e.getMessage());
//        }
//    }
}
