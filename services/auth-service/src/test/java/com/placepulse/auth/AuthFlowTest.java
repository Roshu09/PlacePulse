package com.placepulse.auth;

import com.jayway.jsonpath.JsonPath;
import com.placepulse.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
                        .content("{\"cgpa\":8.25,\"course\":\"MCA\",\"batch\":\"2025-27\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(8.25));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(8.25))
                .andExpect(jsonPath("$.course").value("MCA"))
                .andExpect(jsonPath("$.batch").value("2025-27"));
    }

    @Test
    void cgpaAboveTenIsRejected() throws Exception {
        String token = registerAndGetToken("asha@example.com", null);

        mvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cgpa\":10.5,\"course\":\"MCA\",\"batch\":\"2025-27\"}"))
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
}
