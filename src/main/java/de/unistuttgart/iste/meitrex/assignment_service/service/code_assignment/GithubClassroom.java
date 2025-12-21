package de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment;

import com.google.gson.*;
import com.github.slugify.Slugify;
import de.unistuttgart.iste.meitrex.assignment_service.exception.ExternalPlatformConnectionException;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.assignment.ExternalCodeAssignmentEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.AssignmentRepository;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.ExternalCodeAssignmentRepository;
import de.unistuttgart.iste.meitrex.common.user_handling.LoggedInUser;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalCourse;
import de.unistuttgart.iste.meitrex.user_service.exception.UserServiceConnectionException;
import de.unistuttgart.iste.meitrex.generated.dto.ExternalServiceProviderDto;
import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import de.unistuttgart.iste.meitrex.generated.dto.AccessToken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitHub Classroom integration for the MEITREX assignment service.
 * <p>
 * This component provides functionality to:
 * <ul>
 *     <li>Fetch and synchronize external courses and assignments from GitHub Classroom</li>
 *     <li>Extract README content and metadata for assignments</li>
 *     <li>Find student repositories based on assignment names and GitHub usernames</li>
 *     <li>Synchronize grading data either in bulk or per student using GitHub Actions workflow logs</li>
 * </ul>
 * <p>
 * It uses the GitHub REST API and HTML rendering API to interact with GitHub Classroom and GitHub Repositories.
 * Authentication is handled using per-user OAuth access tokens, retrieved from {@link UserServiceClient}.
 * </p>
 *
 * <p><b>Note:</b> This class is marked with {@code @Primary}, meaning it is the default implementation
 * of {@link CodeAssessmentProvider} in Spring’s context.</p>
 *
 * @see CodeAssessmentProvider
 * @see ExternalCodeAssignmentEntity
 * @see ExternalGrading
 * @see UserServiceClient
 */

@Slf4j
@Component
@Primary
public class GithubClassroom implements CodeAssessmentProvider {

    private final static ExternalServiceProviderDto NAME = ExternalServiceProviderDto.GITHUB;
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_API_VERSION = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String ACCEPT_HEADER_JSON = "application/vnd.github+json";
    private static final String ACCEPT_HEADER_HTML = "application/vnd.github.html+json";
    private final String basePath;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final UserServiceClient userServiceClient;
    private final AssignmentRepository assignmentRepository;
    private final ExternalCodeAssignmentRepository externalCodeAssignmentRepository;

    public GithubClassroom(UserServiceClient userServiceClient, AssignmentRepository assignmentRepository,
                           ExternalCodeAssignmentRepository externalCodeAssignmentRepository, @Value("${github.api_basePath:https://api.github.com}") String basePath) {
        this.userServiceClient = userServiceClient;
        this.assignmentRepository = assignmentRepository;
        this.externalCodeAssignmentRepository = externalCodeAssignmentRepository;
        this.basePath = basePath;
    }

    @Override
    public void syncAssignmentsForCourse(String courseTitle, LoggedInUser currentUser) throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();

            HttpRequest classroomsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/classrooms"))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
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
                    .uri(URI.create(basePath + "/classrooms/" + classroomId + "/assignments"))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
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

                boolean codeAssignmentAlreadyExists = assignmentRepository.existsByExternalId(assignmentId);

                if (codeAssignmentAlreadyExists) {
                    log.info("Assignment '{}' already exists. Skipping sync.", assignmentName);
                    continue;
                }

                fetchedAssignmentNames.add(assignmentName);
                String assignmentLink = classroom.get("url").getAsString() + "/assignments/" + assignment.get("slug").getAsString();
                String invitationLink = assignment.get("invite_link").getAsString();
                JsonElement deadlineElement = assignment.get("deadline");
                OffsetDateTime dueDate = null;
                if (deadlineElement != null && !deadlineElement.isJsonNull()) {
                    dueDate = OffsetDateTime.parse(deadlineElement.getAsString());
                }

                // Fetch README
                String readmeHtml = "";
                try {
                    HttpRequest assignmentDetailsRequest = HttpRequest.newBuilder()
                            .uri(URI.create(basePath + "/assignments/" + assignmentId))
                            .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                            .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                            .header(HEADER_API_VERSION, API_VERSION)
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
                            .uri(URI.create(basePath + "/repos/" + fullName + "/readme"))
                            .header(HEADER_ACCEPT, ACCEPT_HEADER_HTML)
                            .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                            .header(HEADER_API_VERSION, API_VERSION)
                            .GET()
                            .build();

                    HttpResponse<String> readmeResponse = client.send(readmeRequest, HttpResponse.BodyHandlers.ofString());
                    if (readmeResponse.statusCode() == 200) {
                        String rawReadmeHtml = readmeResponse.body();
                        // Clean GitHub README HTML by removing visual noise (e.g., anchor icons)
                        readmeHtml = rawReadmeHtml
                                // Remove full <a class="anchor">...</a> blocks including embedded SVG link icons
                                .replaceAll("<a[^>]*class=\"anchor\"[^>]*>\\s*<svg[^>]*>[^<]*</svg>\\s*</a>", "")
                                // Remove empty lines left behind after tag removal
                                .replaceAll("(?m)^\\s+$", "");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while fetching README", e);
                } catch (IOException e) {
                    log.warn("Failed to fetch README from GitHub", e);
                    // If README fetch fails, we can still save the assignment without it
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalPlatformConnectionException("Interrupted while fetching data from GitHub Classroom", e);
        } catch (IOException | IllegalStateException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch data from GitHub Classroom", e);
        }
    }

    private JsonObject findByNameIgnoreCase(JsonArray array, String fieldName, String targetName) {
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has(fieldName) && obj.get(fieldName).getAsString().equalsIgnoreCase(targetName)) {
                return obj;
            }
        }
        // we can only get to this method if getExternalCourse was called and returned the value of targetName
        // if no classroom exists with the same title, we throw an exception
        throw new IllegalStateException("No element found with " + fieldName + " matching: " + targetName);
    }

    @Override
    public List<ExternalGrading> syncGrades(final String externalAssignmentId, final LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/assignments/" + externalAssignmentId + "/grades"))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
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

                OffsetDateTime submissionDate = null;
                String timestamp = obj.get("submission_timestamp").getAsString();
                if (timestamp != null && !timestamp.isBlank()) {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                                .withZone(ZoneId.of("UTC"));
                        ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestamp, formatter);
                        submissionDate = zonedDateTime.toOffsetDateTime();
                    } catch (DateTimeParseException ignored) {
                    }
                }

                gradings.add(new ExternalGrading(username, null, submissionDate, null, achieved, total));
            }

            return gradings;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalPlatformConnectionException("Interrupted while fetching grades", e);
        } catch (IOException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch grades", e);
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
            HttpRequest runRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/repos/" + owner + "/" + repo + "/actions/runs?per_page=1"))
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> runsResponse = client.send(runRequest, HttpResponse.BodyHandlers.ofString());
            if (runsResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch workflow runs: " + runsResponse.body());
            }

            JsonArray runs = JsonParser.parseString(runsResponse.body())
                    .getAsJsonObject().getAsJsonArray("workflow_runs");
            if (runs.isEmpty()) {
                throw new ExternalPlatformConnectionException("No completed workflow runs found.");
            }

            JsonObject run = runs.get(0).getAsJsonObject();

            String status = run.get("status").getAsString();
            String logsUrl = run.get("logs_url").getAsString();
            String lastlyTested = run.get("updated_at").getAsString();

            if (!status.equals("completed")){
                return new ExternalGrading(null, status, OffsetDateTime.parse(lastlyTested), null, null, null);
            }

            // Download logs
            HttpRequest logRequest = HttpRequest.newBuilder()
                    .uri(URI.create(logsUrl))
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_API_VERSION, API_VERSION)
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
                    if (!entry.getName().endsWith("run-autograding-tests.txt")) {
                        continue;
                    }

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
                        return new ExternalGrading(null, status, OffsetDateTime.parse(lastlyTested), tableHtml, totalPoints, maxPoints);
                    } else {
                        throw new ExternalPlatformConnectionException("Could not find totalPoints/maxPoints in logs.");
                    }
                }

                throw new ExternalPlatformConnectionException("No grading file found in logs.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalPlatformConnectionException("Interrupted while fetching student grade", e);
        } catch (IOException e) {
            throw new ExternalPlatformConnectionException("Failed to fetch student grade", e);
        }

    }

    @Override
    public ExternalServiceProviderDto getName() {
        return NAME;
    }

    private String extractGradingTableAsHtml(String logs) {
        // Remove ANSI escape sequences
        logs = logs.replaceAll("\u001B\\[[;\\d]*m", "");

        String[] lines = logs.split("\n");
        StringBuilder tableHtml = new StringBuilder("<table border=\"1\">\n");

        // Step 1: Build an ordered list of runner test names
        List<String> resolvedNames = new ArrayList<>();
        Pattern processingPattern = Pattern.compile("Processing: ([\\w\\-]+)");
        Pattern titlePattern = Pattern.compile("[✅❌] ([A-Z].+)");
        String lastRunner = null;

        for (String line : lines) {
            Matcher m1 = processingPattern.matcher(line);
            Matcher m2 = titlePattern.matcher(line);
            if (m1.find()) {
                lastRunner = m1.group(1).trim();
            } else if (m2.find() && lastRunner != null) {
                resolvedNames.add(m2.group(1).trim());
                lastRunner = null;
            }
        }

        // Step 2: Extract the grading table and match by row order
        boolean inTable = false;
        int resolvedIndex = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.contains("Test runner summary")) {
                inTable = true;
                continue;
            }
            if (!inTable) continue;

            if (line.startsWith("┌") || line.startsWith("├") || line.startsWith("└") || line.isEmpty()) {
                continue; // Skip borders and empty lines
            }

            String[] cells = line.split("│");
            if (cells.length < 3) continue;

            if (tableHtml.indexOf("<thead>") == -1) {
                // Header
                tableHtml.append("<thead><tr>");
                for (int i = 1; i < cells.length - 1; i++) {
                    tableHtml.append("<th>").append(cells[i].trim()).append("</th>");
                }
                tableHtml.append("<th>Error logs</th>");
                tableHtml.append("</tr></thead>\n<tbody>\n");
            } else {
                if (cells[1].trim().equalsIgnoreCase("Total:")) {
                    break;
                }

                String testScore = cells[2].trim();
                String maxScore = cells[3].trim();
                String resolvedName = resolvedIndex < resolvedNames.size() ? resolvedNames.get(resolvedIndex) : "Unknown";
                resolvedIndex++;

                tableHtml.append("<tr><td>").append(resolvedName)
                        .append("</td><td style=\"text-align:center;\">")
                        .append(testScore).append("/").append(maxScore)
                        .append("</td>");

                if (testScore.equals("0")) {
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains(resolvedName)) {
                            int start = i;
                            for (int j = i + 1; j < lines.length; j++) {
                                if (lines[j].contains("##[endgroup]")) {
                                    start = j + 1;
                                    break;
                                }
                            }

                            StringBuilder errorLogs = new StringBuilder();
                            for (int j = start; j < lines.length; j++) {
                                if (lines[j].contains("##[group]")) break;
                                errorLogs.append(lines[j].trim()).append("<br>");
                            }

                            String sanitized = errorLogs.toString()
                                    .replace("<br>", "___BR___") // temp
                                    .replaceAll("<", "&lt;")
                                    .replaceAll(">", "&gt;")
                                    .replace("___BR___", "<br>");

                            tableHtml.append("<td colspan=\"1\" style=\"white-space: pre-wrap;\">")
                                    .append(sanitized)
                                    .append("</td>");
                            break;
                        }
                    }
                } else {
                    tableHtml.append("<td></td>");
                }

                tableHtml.append("</tr>\n");
            }
        }

        tableHtml.append("</tbody></table>");

        return tableHtml.toString();
    }

    @Override
    public String findRepository(final String assignmentName, final String organizationName, final LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        log.info("[GITHUB-API] >>> findRepository START - assignmentName={}, organizationName={}, userId={}", 
                assignmentName, organizationName, currentUser.getId());
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();
            String githubUsername = queryTokenResponse.getExternalUserId();

            final Slugify slg = Slugify.builder().build();
            String slug = slg.slugify(assignmentName);
            String repoName = slug + "-" + githubUsername;
            log.info("[GITHUB-API] Constructed repository name: {}", repoName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/repos/" + organizationName + "/" + repoName))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[GITHUB-API] GitHub API response status: {}", response.statusCode());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch repository: " + response.body());
            }

            JsonObject repo = JsonParser.parseString(response.body()).getAsJsonObject();
            return repo.has("html_url") ? repo.get("html_url").getAsString() : null;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[GITHUB-API] Interrupted while fetching student repo link");
            throw new ExternalPlatformConnectionException("Interrupted while fetching student repo link", e);
        } catch (IOException e) {
            log.error("[GITHUB-API] IOException while fetching student repo link");
            throw new ExternalPlatformConnectionException("Error fetching student repo link", e);
        }

    }

    public ExternalCourse getExternalCourse(final String courseTitle, final LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        try {
            AccessToken queryTokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = queryTokenResponse.getAccessToken();

            HttpRequest classroomsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/classrooms"))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> classroomsResponse = client.send(classroomsRequest, HttpResponse.BodyHandlers.ofString());
            if (classroomsResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch classrooms: " + classroomsResponse.body());
            }

            JsonArray classrooms = JsonParser.parseString(classroomsResponse.body()).getAsJsonArray();
            JsonObject classroom = findByNameIgnoreCase(classrooms, "name", courseTitle);

            long classroomId = classroom.get("id").getAsLong();
            HttpRequest courseRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/classrooms/" + classroomId))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> courseResponse = client.send(courseRequest, HttpResponse.BodyHandlers.ofString());
            if (courseResponse.statusCode() != 200) {
                throw new ExternalPlatformConnectionException("Failed to fetch course details: " + courseResponse.body());
            }

            classroom = JsonParser.parseString(courseResponse.body()).getAsJsonObject();

            JsonObject organization = classroom.getAsJsonObject("organization");
            String organizationName = organization.has("login") ? organization.get("login").getAsString() : null;

            return new ExternalCourse(classroom.get("name").getAsString(), classroom.get("url").getAsString(), organizationName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalPlatformConnectionException("Interrupted while fetching external course", e);
        } catch (IOException e) {
            throw new ExternalPlatformConnectionException("Error fetching external course", e);
        }
    }
    
    /**
     * Fetches the source code from a student's GitHub repository.
     * Retrieves all files from the repository's default branch.
     *
     * @param repoLink the GitHub repository URL
     * @param currentUser the currently logged-in user
     * @return StudentCodeSubmission containing all source files and metadata
     * @throws ExternalPlatformConnectionException if the GitHub API is unreachable or returns an error
     * @throws UserServiceConnectionException if user-related data cannot be resolved
     */
    public StudentCodeSubmission fetchStudentCode(String repoLink, LoggedInUser currentUser)
            throws ExternalPlatformConnectionException, UserServiceConnectionException {
        log.info("[GITHUB-API] >>> fetchStudentCode START - repoLink={}, userId={}", repoLink, currentUser.getId());
        try {
            AccessToken tokenResponse = userServiceClient.queryAccessToken(currentUser, NAME);
            String token = tokenResponse.getAccessToken();

            URI uri = URI.create(repoLink);
            String[] parts = uri.getPath().split("/");
            if (parts.length < 3) {
                log.error("[GITHUB-API] Invalid repo URL format: {}, parts count: {}", repoLink, parts.length);
                throw new ExternalPlatformConnectionException("Invalid repo URL: " + repoLink);
            }
            String owner = parts[1];
            String repo = parts[2];
            log.info("[GITHUB-API] Parsed repository - owner={}, repo={}", owner, repo);

            HttpRequest repoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/repos/" + owner + "/" + repo))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> repoResponse = client.send(repoRequest, HttpResponse.BodyHandlers.ofString());
            log.info("[GITHUB-API] Repository info response status: {}", repoResponse.statusCode());
            
            if (repoResponse.statusCode() != 200) {
                log.error("[GITHUB-API] Failed to fetch repository info: status={}, body={}", 
                        repoResponse.statusCode(), repoResponse.body());
                throw new ExternalPlatformConnectionException("Failed to fetch repository info: " + repoResponse.body());
            }

            JsonObject repoInfo = JsonParser.parseString(repoResponse.body()).getAsJsonObject();
            String defaultBranch = repoInfo.get("default_branch").getAsString();

            HttpRequest commitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(basePath + "/repos/" + owner + "/" + repo + "/commits/" + defaultBranch))
                    .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                    .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                    .header(HEADER_API_VERSION, API_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> commitResponse = client.send(commitRequest, HttpResponse.BodyHandlers.ofString());
            log.info("[GITHUB-API] Commit info response status: {}", commitResponse.statusCode());
            
            if (commitResponse.statusCode() != 200) {
                log.error("[GITHUB-API] Failed to fetch commit info: status={}, body={}", 
                        commitResponse.statusCode(), commitResponse.body());
                throw new ExternalPlatformConnectionException("Failed to fetch commit info: " + commitResponse.body());
            }

            JsonObject commitInfo = JsonParser.parseString(commitResponse.body()).getAsJsonObject();
            String commitSha = commitInfo.get("sha").getAsString();
            String commitDateStr = commitInfo.getAsJsonObject("commit")
                    .getAsJsonObject("committer")
                    .get("date").getAsString();
            OffsetDateTime commitDate = OffsetDateTime.parse(commitDateStr);

            Map<String, String> files = new HashMap<>();
            log.info("[GITHUB-API] Starting recursive file fetch from repository");
            fetchFilesRecursively(owner, repo, defaultBranch, "", token, files);
            log.info("[GITHUB-API] Finished fetching files - total files retrieved: {}", files.size());

            return StudentCodeSubmission.builder()
                    .studentId(currentUser.getId())
                    .repositoryUrl(repoLink)
                    .commitSha(commitSha)
                    .commitTimestamp(commitDate)
                    .files(files)
                    .branch(defaultBranch)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[GITHUB-API] Interrupted while fetching student code from {}", repoLink);
            throw new ExternalPlatformConnectionException("Interrupted while fetching student code", e);
        } catch (IOException e) {
            log.error("[GITHUB-API] IOException while fetching student code from {}", repoLink);
            throw new ExternalPlatformConnectionException("Error fetching student code", e);
        } catch (Exception e) {
            log.error("[GITHUB-API] Unexpected error while fetching student code from {}", repoLink);
            throw new ExternalPlatformConnectionException("Unexpected error fetching student code: " + e.getMessage());
        }
    }

    /**
     * Recursively fetches all files from a GitHub repository directory.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param branch branch name
     * @param path current path in the repository (empty string for root)
     * @param token GitHub access token
     * @param files map to store file paths and their content
     */
    private void fetchFilesRecursively(String owner, String repo, String branch, String path, 
                                      String token, Map<String, String> files) 
            throws IOException, InterruptedException, ExternalPlatformConnectionException {
        
        String urlPath = path.isEmpty() ? "" : "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(basePath + "/repos/" + owner + "/" + repo + "/contents" + urlPath + "?ref=" + branch))
                .header(HEADER_ACCEPT, ACCEPT_HEADER_JSON)
                .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                .header(HEADER_API_VERSION, API_VERSION)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("[GITHUB-API] Failed to fetch contents for path '{}': status={}, body={}", 
                    path, response.statusCode(), response.body());
            return;
        }

        JsonArray contents = JsonParser.parseString(response.body()).getAsJsonArray();
        
        for (JsonElement element : contents) {
            JsonObject item = element.getAsJsonObject();
            String type = item.get("type").getAsString();
            String itemPath = item.get("path").getAsString();
            
            if ("file".equals(type)) {
                String downloadUrl = item.get("download_url").getAsString();
                HttpRequest fileRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header(HEADER_AUTHORIZATION, TOKEN_PREFIX + token)
                        .GET()
                        .build();
                
                HttpResponse<String> fileResponse = client.send(fileRequest, HttpResponse.BodyHandlers.ofString());
                if (fileResponse.statusCode() == 200) {
                    files.put(itemPath, fileResponse.body());
                } else {
                    log.warn("[GITHUB-API] Failed to fetch file content for: {}, status={}", itemPath, fileResponse.statusCode());
                }
            } else if ("dir".equals(type)) {
                fetchFilesRecursively(owner, repo, branch, itemPath, token, files);
            }
        }
    }
}
