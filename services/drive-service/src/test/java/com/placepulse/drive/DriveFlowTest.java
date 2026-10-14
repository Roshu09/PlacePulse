package com.placepulse.drive;

import com.jayway.jsonpath.JsonPath;
import com.placepulse.drive.client.ProfileClient;
import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.events.DriveEvents;
import com.placepulse.drive.events.EventPublisher;
import com.placepulse.drive.exception.ApiException;
import com.placepulse.drive.repository.DriveApplicationRepository;
import com.placepulse.drive.repository.DriveRepository;
import com.placepulse.drive.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // no broker either: we only check what would have been published
    @MockBean EventPublisher events;

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
        profileIs("8.20", "MCA", "2027");
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

    /** `batches` is a comma list of pass-out years like "2026,2027", or null for every batch. */
    private String driveJson(String company, String minCgpa, String batches, LocalDate deadline) {
        String batch = batches == null ? "" : ",\"eligibleBatches\":[" + batches + "]";
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
        postDrive(tprToken, driveJson("Acme", "7.5", "2027", LocalDate.now().plusDays(7)))
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
        UUID acme = createDrive("Acme", "7.5", "2027");

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
        profileIs(null, "MCA", "2027");
        UUID acme = createDrive("Acme", "6.0", null);

        apply(ashaToken, acme)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Add your CGPA")));
    }

    @Test
    void studentFromAnotherBatchIsRejected() throws Exception {
        UUID acme = createDrive("Acme", "6.0", "2025,2026");

        apply(ashaToken, acme)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("2025, 2026")));
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

    // ---- events ---------------------------------------------------------------------------

    @Test
    void creatingADrivePublishesDriveCreated() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(acme.toString()), eq(DriveEvents.DRIVE_CREATED), payload.capture());
        DriveEvents.DriveCreated created = (DriveEvents.DriveCreated) payload.getValue();
        assertThat(created.company()).isEqualTo("Acme");
        assertThat(created.createdBy()).isEqualTo(tprId);
    }

    @Test
    void applyingPublishesApplicationSubmittedWithTheDriveOwner() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(applicationId), eq(DriveEvents.APPLICATION_SUBMITTED), payload.capture());
        DriveEvents.ApplicationSubmitted submitted = (DriveEvents.ApplicationSubmitted) payload.getValue();
        assertThat(submitted.studentId()).isEqualTo(ashaId);
        assertThat(submitted.driveCreatedBy()).isEqualTo(tprId);
        assertThat(submitted.company()).isEqualTo("Acme");
    }

    @Test
    void aRejectedApplicationPublishesNothing() throws Exception {
        profileIs("5.0", "MCA", "2027");
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isUnprocessableEntity());
        verify(events, never()).publish(any(), eq(DriveEvents.APPLICATION_SUBMITTED), any());
    }

    @Test
    void changingAStagePublishesFromAndTo() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        patchJson("/api/applications/" + applicationId + "/stage", tprToken, "{\"stage\":\"SHORTLISTED\"}")
                .andExpect(status().isOk());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(applicationId), eq(DriveEvents.STAGE_CHANGED), payload.capture());
        DriveEvents.StageChanged changed = (DriveEvents.StageChanged) payload.getValue();
        assertThat(changed.fromStage()).isEqualTo("APPLIED");
        assertThat(changed.toStage()).isEqualTo("SHORTLISTED");
        assertThat(changed.studentId()).isEqualTo(ashaId);
    }

    @Test
    void settingTheSameStageAgainPublishesNothing() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        MvcResult applied = apply(ashaToken, acme).andExpect(status().isCreated()).andReturn();
        String applicationId = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");

        patchJson("/api/applications/" + applicationId + "/stage", tprToken, "{\"stage\":\"APPLIED\"}")
                .andExpect(status().isOk());
        verify(events, never()).publish(any(), eq(DriveEvents.STAGE_CHANGED), any());
    }

    @Test
    void aBrokenBrokerNeverFailsTheUsersAction() throws Exception {
        doThrow(new IllegalStateException("broker down")).when(events).publish(any(), any(), any());
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());
        assertThat(applications.count()).isEqualTo(1);
    }

    // ---- several batches --------------------------------------------------------------------

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void aDriveForSeveralBatchesAcceptsAnyOfThem() throws Exception {
        UUID acme = createDrive("Acme", "6.0", "2026,2027,2028");
        apply(ashaToken, acme).andExpect(status().isCreated()); // 2027 student
    }

    @Test
    void aDriveShowsItsBatchesAsAList() throws Exception {
        createDrive("Acme", "6.0", "2027,2026");
        mvc.perform(get("/api/drives").header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$[0].eligibleBatches[0]").value(2026))
                .andExpect(jsonPath("$[0].eligibleBatches[1]").value(2027));
    }

    @Test
    void aProfileSavedInTheOldRangeFormatCountsAsItsPassOutYear() throws Exception {
        profileIs("8.0", "MCA", "2025-27");
        UUID acme = createDrive("Acme", "6.0", "2027");
        apply(ashaToken, acme).andExpect(status().isCreated());
    }

    // ---- editing a drive ---------------------------------------------------------------------

    private ResultActions putJson(UUID id, String token, String json) throws Exception {
        return mvc.perform(put("/api/drives/" + id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void tprCanEditADriveAndStudentsSeeTheChange() throws Exception {
        UUID acme = createDrive("Acme", "7.0", "2027");

        putJson(acme, tprToken, driveJson("Acme Labs", "8.0", "2026,2027", LocalDate.now().plusDays(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Acme Labs"))
                .andExpect(jsonPath("$.minCgpa").value(8.0));

        mvc.perform(get("/api/drives/" + acme).header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$.company").value("Acme Labs"))
                .andExpect(jsonPath("$.eligibleBatches.length()").value(2));
    }

    @Test
    void editingPublishesDriveUpdatedListingWhatChanged() throws Exception {
        UUID acme = createDrive("Acme", "7.0", "2027");
        putJson(acme, tprToken, driveJson("Acme", "8.0", "2027", LocalDate.now().plusDays(7))).andExpect(status().isOk());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(acme.toString()), eq(DriveEvents.DRIVE_UPDATED), payload.capture());
        DriveEvents.DriveUpdated updated = (DriveEvents.DriveUpdated) payload.getValue();
        assertThat(updated.changes()).containsExactly("minimum CGPA");
        assertThat(updated.createdBy()).isEqualTo(tprId);
    }

    @Test
    void savingADriveWithoutChangesPublishesNothing() throws Exception {
        UUID acme = createDrive("Acme", "7.0", "2027");
        putJson(acme, tprToken, driveJson("Acme", "7.0", "2027", LocalDate.now().plusDays(7))).andExpect(status().isOk());
        verify(events, never()).publish(any(), eq(DriveEvents.DRIVE_UPDATED), any());
    }

    @Test
    void editingKeepsApplicationsThatAlreadyExist() throws Exception {
        UUID acme = createDrive("Acme", "7.0", "2027");
        apply(ashaToken, acme).andExpect(status().isCreated());

        // now only 2026 may apply, but Asha's application stays
        putJson(acme, tprToken, driveJson("Acme", "9.0", "2026", LocalDate.now().plusDays(7))).andExpect(status().isOk());

        mvc.perform(get("/api/drives/" + acme + "/applications").header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$.length()").value(1));
        // and a new applicant is judged by the new rules
        profileIs("8.0", "MCA", "2027");
        apply(raviToken, acme).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void studentsCannotEditDrives() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        putJson(acme, ashaToken, driveJson("Hacked", "0", null, LocalDate.now().plusDays(7))).andExpect(status().isForbidden());
    }

    @Test
    void editingAnUnknownDriveIsNotFound() throws Exception {
        putJson(UUID.randomUUID(), tprToken, driveJson("Acme", "7.0", null, LocalDate.now().plusDays(7)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLastDateCannotBeMovedIntoThePast() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        putJson(acme, tprToken, driveJson("Acme", "7.0", null, LocalDate.now().minusDays(1))).andExpect(status().isBadRequest());
    }

    // ---- logos ---------------------------------------------------------------------------

    private ResultActions multipartDrive(HttpMethod method, String path, String json, MockMultipartFile logo) throws Exception {
        // the builder is mutable: calling file() on it directly keeps the multipart type that header() would lose
        MockMultipartHttpServletRequestBuilder request = multipart(method, path);
        request.file(new MockMultipartFile("drive", "", "application/json", json.getBytes(StandardCharsets.UTF_8)));
        request.header("Authorization", bearer(tprToken));
        if (logo != null) {
            request.file(logo);
        }
        return mvc.perform(request);
    }

    @Test
    void aDriveCanBeCreatedWithAPngLogoAndAnyoneCanFetchIt() throws Exception {
        MvcResult created = multipartDrive(HttpMethod.POST, "/api/drives", driveJson("Acme", "7.0", null, LocalDate.now().plusDays(7)),
                new MockMultipartFile("logo", "acme.png", "image/png", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logoUrl", containsString("/logo?v=")))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // no token: the logo is shown in an <img> tag
        mvc.perform(get("/api/drives/" + id + "/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void aDriveWithoutALogoHasNoLogoUrlAndNoLogo() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        mvc.perform(get("/api/drives/" + acme).header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        mvc.perform(get("/api/drives/" + acme + "/logo")).andExpect(status().isNotFound());
    }

    @Test
    void aFileThatIsNotAPngIsRejectedEvenIfItIsNamedPng() throws Exception {
        multipartDrive(HttpMethod.POST, "/api/drives", driveJson("Acme", "7.0", null, LocalDate.now().plusDays(7)),
                new MockMultipartFile("logo", "acme.png", "image/png", "<html>not an image</html>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.logo", containsString("PNG")));
        assertThat(drives.count()).isZero();
    }

    @Test
    void aLogoOverTheLimitIsRejected() throws Exception {
        byte[] big = new byte[600 * 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        multipartDrive(HttpMethod.POST, "/api/drives", driveJson("Acme", "7.0", null, LocalDate.now().plusDays(7)),
                new MockMultipartFile("logo", "acme.png", "image/png", big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.logo", containsString("500 KB")));
    }

    @Test
    void aLogoCanBeAddedLaterReplacedAndRemoved() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        String json = driveJson("Acme", "7.0", null, LocalDate.now().plusDays(7));

        multipartDrive(HttpMethod.PUT, "/api/drives/" + acme, json, new MockMultipartFile("logo", "a.png", "image/png", PNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl", containsString("/logo?v=")));
        mvc.perform(get("/api/drives/" + acme + "/logo")).andExpect(status().isOk());

        String withRemove = json.substring(0, json.length() - 1) + ",\"removeLogo\":true}";
        multipartDrive(HttpMethod.PUT, "/api/drives/" + acme, withRemove, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        mvc.perform(get("/api/drives/" + acme + "/logo")).andExpect(status().isNotFound());
    }

    // ---- removing an application -----------------------------------------------------------------

    private UUID applicationIdOf(UUID driveId) throws Exception {
        MvcResult r = mvc.perform(get("/api/drives/" + driveId + "/applications").header("Authorization", bearer(tprToken))).andReturn();
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$[0].id"));
    }

    @Test
    void tprCanRemoveAnApplicationSoTheStudentCanApplyAgain() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());
        UUID applicationId = applicationIdOf(acme);

        mvc.perform(delete("/api/applications/" + applicationId).header("Authorization", bearer(tprToken)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/drives/" + acme + "/applications").header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/drives").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$[0].applied").value(false));
        apply(ashaToken, acme).andExpect(status().isCreated());
    }

    @Test
    void removingAnApplicationTellsTheStudent() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());
        UUID applicationId = applicationIdOf(acme);
        mvc.perform(delete("/api/applications/" + applicationId).header("Authorization", bearer(tprToken)));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq(applicationId.toString()), eq(DriveEvents.APPLICATION_REMOVED), payload.capture());
        DriveEvents.ApplicationRemoved removed = (DriveEvents.ApplicationRemoved) payload.getValue();
        assertThat(removed.company()).isEqualTo("Acme");
        assertThat(removed.studentName()).isEqualTo("Asha Verma");
    }

    @Test
    void studentsCannotRemoveApplications() throws Exception {
        UUID acme = createDrive("Acme", "7.0", null);
        apply(ashaToken, acme).andExpect(status().isCreated());
        UUID applicationId = applicationIdOf(acme);
        mvc.perform(delete("/api/applications/" + applicationId).header("Authorization", bearer(ashaToken)))
                .andExpect(status().isForbidden());
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void removingAnUnknownApplicationIsNotFound() throws Exception {
        mvc.perform(delete("/api/applications/" + UUID.randomUUID()).header("Authorization", bearer(tprToken)))
                .andExpect(status().isNotFound());
    }
}
