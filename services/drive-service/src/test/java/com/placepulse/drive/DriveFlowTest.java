package com.placepulse.drive;

import com.jayway.jsonpath.JsonPath;
import com.placepulse.drive.client.ProfileClient;
import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.exception.ApiException;
import com.placepulse.drive.repository.DriveApplicationRepository;
import com.placepulse.drive.repository.DriveRepository;
import com.placepulse.drive.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriveFlowTest {

    @Autowired MockMvc mvc;
    @Autowired DriveRepository drives;
    @Autowired DriveApplicationRepository applications;
    @Value("${app.jwt.secret}") String jwtSecret;

    // auth-service is not running in these tests, so its answer is faked
    @MockBean ProfileClient profileClient;

    private final UUID tprId = UUID.randomUUID();
    private final UUID ashaId = UUID.randomUUID();
    private final UUID raviId = UUID.randomUUID();

    private String tprToken;
    private String ashaToken;
    private String raviToken;

    @BeforeEach
    void setUp() {
        applications.deleteAll();
        drives.deleteAll();
        tprToken = token(tprId, "tpr@example.com", Role.TPR);
        ashaToken = token(ashaId, "asha@example.com", Role.STUDENT);
        raviToken = token(raviId, "ravi@example.com", Role.STUDENT);
        // by default the student is a strong, in-batch candidate
        profileIs("8.20", "MCA", "2025-27");
    }

    // ---- helpers --------------------------------------------------------------------------

    private String token(UUID id, String email, Role role) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void profileIs(String cgpa, String course, String batch) {
        when(profileClient.fetch(anyString())).thenReturn(new StudentProfile(
                UUID.randomUUID(), "asha@example.com", "Asha Verma", "STUDENT",
                cgpa == null ? null : new BigDecimal(cgpa), course, batch));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String driveJson(String company, String minCgpa, String eligibleBatch, LocalDate deadline) {
        String batch = eligibleBatch == null ? "" : ",\"eligibleBatch\":\"" + eligibleBatch + "\"";
        return "{\"company\":\"" + company + "\",\"roleTitle\":\"SDE Intern\",\"location\":\"Bengaluru\","
                + "\"packageLpa\":12.5,\"minCgpa\":" + minCgpa + ",\"deadline\":\"" + deadline + "\"" + batch + "}";
    }

    private ResultActions postDrive(String token, String json) throws Exception {
        return mvc.perform(post("/api/drives")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Creates a drive as the TPR and returns its id. */
    private UUID createDrive(String company, String minCgpa, String eligibleBatch) throws Exception {
        MvcResult result = postDrive(tprToken, driveJson(company, minCgpa, eligibleBatch, LocalDate.now().plusDays(7)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private ResultActions apply(String token, UUID driveId) throws Exception {
        return mvc.perform(post("/api/drives/" + driveId + "/apply").header("Authorization", bearer(token)));
    }

    private ResultActions patchJson(String path, String token, String json) throws Exception {
        return mvc.perform(patch(path)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---- creating drives ------------------------------------------------------------------

    @Test
    void tprCanCreateADrive() throws Exception {
        postDrive(tprToken, driveJson("Acme", "7.5", "2025-27", LocalDate.now().plusDays(7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.acceptingApplications").value(true))
                .andExpect(jsonPath("$.applicantCount").value(0));
    }

    @Test
    void studentCannotCreateADrive() throws Exception {
        postDrive(ashaToken, driveJson("Acme", "7.5", null, LocalDate.now().plusDays(7)))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingADriveWithoutACompanyIsRejected() throws Exception {
        String json = "{\"roleTitle\":\"SDE\",\"minCgpa\":7,\"deadline\":\"" + LocalDate.now().plusDays(7) + "\"}";
        postDrive(tprToken, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.company").exists());
    }

    @Test
    void aDeadlineInThePastIsRejected() throws Exception {
        postDrive(tprToken, driveJson("Acme", "7.5", null, LocalDate.now().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deadline").exists());
    }

    @Test
    void anUnauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/drives")).andExpect(status().isUnauthorized());
    }

    // ---- listing drives -------------------------------------------------------------------

    @Test
    void studentsSeeOnlyDrivesTheyCanStillApplyTo() throws Exception {
        createDrive("Acme", "7.0", null);

        UUID closed = createDrive("Globex", "7.0", null);
        patchJson("/api/drives/" + closed + "/status", tprToken, "{\"status\":\"CLOSED\"}")
                .andExpect(status().isOk());

        // a drive whose last date has passed (inserted directly, the API refuses past deadlines)
        drives.save(new Drive("Initech", "SDE", null, null, null, new BigDecimal("6.0"), null,
                LocalDate.now().minusDays(1), tprId));

        mvc.perform(get("/api/drives").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Acme"))
                .andExpect(jsonPath("$[0].applied").value(false));
    }

    @Test
    void tprSeesEveryDriveWithApplicantCounts() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        createDrive("Globex", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());

        MvcResult result = mvc.perform(get("/api/drives").header("Authorization", bearer(tprToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        List<Integer> acmeCount = JsonPath.read(result.getResponse().getContentAsString(),
                "$[?(@.company=='Acme')].applicantCount");
        List<Integer> globexCount = JsonPath.read(result.getResponse().getContentAsString(),
                "$[?(@.company=='Globex')].applicantCount");
        assertThat(acmeCount).containsExactly(1);
        assertThat(globexCount).containsExactly(0);
    }

    @Test
    void studentSeesThatTheyAlreadyApplied() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());

        mvc.perform(get("/api/drives").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$[0].applied").value(true));
    }

    // ---- applying -------------------------------------------------------------------------

    @Test
    void eligibleStudentCanApply() throws Exception {
        UUID acme = createDrive("Acme", "7.5", "2025-27");

        apply(ashaToken, acme)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.studentName").value("Asha Verma"))
                .andExpect(jsonPath("$.cgpa").value(8.2));
    }

    @Test
    void studentBelowTheMinimumCgpaIsRejected() throws Exception {
        UUID acme = createDrive("Acme", "9.0", null);

        apply(ashaToken, acme)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("CGPA")));
    }

    @Test
    void studentWithoutACgpaIsToldToAddOne() throws Exception {
        profileIs(null, "MCA", "2025-27");
        UUID acme = createDrive("Acme", "6.0", null);

        apply(ashaToken, acme)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Add your CGPA")));
    }

    @Test
    void studentFromAnotherBatchIsRejected() throws Exception {
        UUID acme = createDrive("Acme", "6.0", "2024-26");

        apply(ashaToken, acme)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("2024-26")));
    }

    @Test
    void applyingTwiceIsRejected() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);

        apply(ashaToken, acme).andExpect(status().isCreated());
        apply(ashaToken, acme).andExpect(status().isConflict());
    }

    @Test
    void closedDriveRejectsApplications() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        patchJson("/api/drives/" + acme + "/status", tprToken, "{\"status\":\"CLOSED\"}")
                .andExpect(status().isOk());

        apply(ashaToken, acme).andExpect(status().isConflict());
    }

    @Test
    void driveWithAPassedDeadlineRejectsApplications() throws Exception {
        Drive expired = drives.save(new Drive("Initech", "SDE", null, null, null, new BigDecimal("6.0"), null,
                LocalDate.now().minusDays(1), tprId));

        apply(ashaToken, expired.getId()).andExpect(status().isConflict());
    }

    @Test
    void applyingToAnUnknownDriveIsNotFound() throws Exception {
        apply(ashaToken, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void tprCannotApply() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);

        apply(tprToken, acme).andExpect(status().isForbidden());
    }

    @Test
    void applyingFailsClearlyWhenTheProfileServiceIsDown() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        when(profileClient.fetch(anyString()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "We couldn't load your profile right now."));

        apply(ashaToken, acme).andExpect(status().isServiceUnavailable());
        // and nothing was saved
        assertThat(applications.count()).isZero();
    }

    // ---- following applications -----------------------------------------------------------

    @Test
    void tprCanReviewApplicantsAndMoveThemThroughTheRounds() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/drives/" + acme + "/applications").header("Authorization", bearer(tprToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("Asha Verma"));

        patchJson("/api/applications/" + applicationId + "/stage", tprToken, "{\"stage\":\"SHORTLISTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SHORTLISTED"));

        mvc.perform(get("/api/applications/mine").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stage").value("SHORTLISTED"))
                .andExpect(jsonPath("$[0].company").value("Acme"));
    }

    @Test
    void anUnknownStageIsRejected() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        patchJson("/api/applications/" + applicationId + "/stage", tprToken, "{\"stage\":\"MAYBE\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentsCannotSeeApplicantsOrChangeStages() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/drives/" + acme + "/applications").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isForbidden());
        patchJson("/api/applications/" + applicationId + "/stage", ashaToken, "{\"stage\":\"OFFER\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void eachStudentOnlySeesTheirOwnApplications() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());
        apply(raviToken, acme).andExpect(status().isCreated());

        mvc.perform(get("/api/applications/mine").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/applications/mine").header("Authorization", bearer(raviToken)))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
