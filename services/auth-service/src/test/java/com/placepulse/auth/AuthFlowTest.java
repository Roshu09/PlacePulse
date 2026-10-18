package com.placepulse.auth;

import com.jayway.jsonpath.JsonPath;
import com.placepulse.auth.events.AuthEvents;
import com.placepulse.auth.events.EventPublisher;
import com.placepulse.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @MockBean
    EventPublisher events;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------

    private String registerJson(String email, String password, String inviteCode) {
        String invite = inviteCode == null ? "" : ",\"inviteCode\":\"" + inviteCode + "\"";
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"fullName\":\"Test User\"" + invite + "}";
    }

    private MvcResult register(String email, String inviteCode, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, PASSWORD, inviteCode)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private String registerAndGetToken(String email, String inviteCode) throws Exception {
        MvcResult result = register(email, inviteCode, 201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    // ---- registration --------------------------------------------------------------------

    @Test
    void registerCreatesStudentAndReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("asha@example.com", PASSWORD, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register("asha@example.com", null, 201);
        register("ASHA@Example.com", null, 409);
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("asha@example.com", "short", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email", PASSWORD, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ---- login ---------------------------------------------------------------------------

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        register("asha@example.com", null, 201);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Asha@Example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        register("asha@example.com", null, 201);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginForUnknownEmailIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- protected endpoints -------------------------------------------------------------

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    // ---- roles ---------------------------------------------------------------------------

    @Test
    void studentCannotAccessTprEndpoint() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(get("/api/tpr/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tprWithInviteCodeCanAccessTprEndpoint() throws Exception {
        String token = registerAndGetToken("tpr@example.com", "test-invite-code");

        mvc.perform(get("/api/tpr/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void wrongInviteCodeIsRejected() throws Exception {
        register("tpr@example.com", "wrong-code", 403);
    }

    // ---- student profile -----------------------------------------------------------------

    @Test
    void profileCanBeSavedAndReadBack() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cgpa\":8.25,\"course\":\"MCA\",\"batch\":\"2027\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(8.25));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(8.25))
                .andExpect(jsonPath("$.course").value("MCA"))
                .andExpect(jsonPath("$.batch").value("2027"))
                .andExpect(jsonPath("$.placementRegistered").value(false))
                .andExpect(jsonPath("$.missingRequired[0]").value("phone"));
    }

    @Test
    void cgpaAboveTenIsRejected() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cgpa\":10.5,\"course\":\"MCA\",\"batch\":\"2027\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cgpa").exists());
    }

    @Test
    void profileUpdateRequiresAToken() throws Exception {
        mvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cgpa\":8.0}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- placement registration and the profile lock ---------------------------------------

    private static final String FULL_PROFILE = "{\"phone\":\"9876543210\",\"course\":\"MCA\",\"batch\":\"2027\","
            + "\"cgpa\":8.25,\"tenthPercent\":88.5,\"twelfthPercent\":84,"
            + "\"resumeUrl\":\"https://example.com/cv.pdf\"%s}";

    private String fullProfile(String extra) {
        return String.format(FULL_PROFILE, extra);
    }

    private void saveProfile(String token, String json, int expected) throws Exception {
        mvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expected));
    }

    private void registerForPlacements(String token, int expected) throws Exception {
        mvc.perform(post("/api/users/me/placement-registration").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }

    @Test
    void signUpAnnouncesTheStudent() throws Exception {
        register("asha@example.com", null, 201);
        verify(events).publish(any(), eq(AuthEvents.STUDENT_REGISTERED), any());
    }

    @Test
    void cannotRegisterWithMissingRequiredFields() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        saveProfile(token, "{\"cgpa\":8.0,\"course\":\"MCA\"}", 200);

        mvc.perform(post("/api/users/me/placement-registration").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.phone").value("Required"))
                .andExpect(jsonPath("$.errors.resumeUrl").exists());
    }

    @Test
    void registeringLocksTheProfileAndIsRepeatable() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        saveProfile(token, fullProfile(""), 200);
        registerForPlacements(token, 200);
        registerForPlacements(token, 200);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.placementRegistered").value(true))
                .andExpect(jsonPath("$.missingRequired").isEmpty());
        // one event for the sign-up, one for the registration; the repeat adds nothing
        verify(events, org.mockito.Mockito.times(2)).publish(any(), eq(AuthEvents.STUDENT_REGISTERED), any());
    }

    @Test
    void editingARegisteredProfileNeedsAcknowledgement() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        saveProfile(token, fullProfile(""), 200);
        registerForPlacements(token, 200);

        saveProfile(token, fullProfile(",\"city\":\"Kanpur\""), 409);
        verify(events, never()).publish(any(), eq(AuthEvents.PROFILE_EDITED), any());

        saveProfile(token, fullProfile(",\"city\":\"Kanpur\",\"acknowledgeDebar\":true"), 200);
        verify(events).publish(any(), eq(AuthEvents.PROFILE_EDITED), any());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.city").value("Kanpur"));
    }

    @Test
    void savingAnUnchangedRegisteredProfileDoesNothing() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        saveProfile(token, fullProfile(""), 200);
        registerForPlacements(token, 200);

        saveProfile(token, fullProfile(""), 200);
        verify(events, never()).publish(any(), eq(AuthEvents.PROFILE_EDITED), any());
    }

    @Test
    void aRegisteredProfileCannotBeEmptiedOfRequiredFields() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        saveProfile(token, fullProfile(""), 200);
        registerForPlacements(token, 200);

        saveProfile(token, "{\"cgpa\":8.25,\"course\":\"MCA\",\"batch\":\"2027\",\"acknowledgeDebar\":true}", 422);
    }

    @Test
    void badLinksAndPercentagesAreRejected() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);
        mvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUrl\":\"not-a-link\",\"tenthPercent\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.githubUrl").exists())
                .andExpect(jsonPath("$.errors.tenthPercent").exists());
    }

    @Test
    void tprHasNoPlacementProfile() throws Exception {
        String token = registerAndGetToken("tpr@example.com", "test-invite-code");
        registerForPlacements(token, 403);
    }
}
