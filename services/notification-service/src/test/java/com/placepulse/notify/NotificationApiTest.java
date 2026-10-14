package com.placepulse.notify;

import com.placepulse.notify.domain.Notification;
import com.placepulse.notify.events.NotificationEventHandler;
import com.placepulse.notify.repository.KnownStudentRepository;
import com.placepulse.notify.repository.NotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired NotificationRepository notifications;
    @Autowired KnownStudentRepository students;
    @Autowired NotificationEventHandler handler;
    @Value("${app.jwt.secret}") String jwtSecret;

    private final UUID ashaId = UUID.randomUUID();
    private final UUID raviId = UUID.randomUUID();
    private final UUID tprId = UUID.randomUUID();
    private String ashaToken;
    private String raviToken;
    private String tprToken;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        students.deleteAll();
        ashaToken = token(ashaId, "asha@example.com", "STUDENT");
        raviToken = token(raviId, "ravi@example.com", "STUDENT");
        tprToken = token(tprId, "tpr@example.com", "TPR");
    }

    private String token(UUID id, String email, String role) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private Notification give(UUID recipient, String title) throws InterruptedException {
        Thread.sleep(5); // distinct timestamps so "newest first" is testable
        return notifications.save(new Notification(UUID.randomUUID(), recipient, "test", title, "body of " + title, "/drives"));
    }

    @Test
    void withoutATokenTheAnswerIs401() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void aNewUserHasNoNotifications() throws Exception {
        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void youSeeOnlyYourOwnNotificationsNewestFirst() throws Exception {
        give(ashaId, "first");
        give(ashaId, "second");
        give(raviId, "not for asha");

        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("second"))
                .andExpect(jsonPath("$.items[1].title").value("first"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].link").value("/drives"));
    }

    @Test
    void tprsGetTheirOwnNotificationsToo() throws Exception {
        give(tprId, "new application");

        mvc.perform(get("/api/notifications").header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void markingOneReadLowersTheUnreadCount() throws Exception {
        Notification a = give(ashaId, "a");
        give(ashaId, "b");

        mvc.perform(post("/api/notifications/" + a.getId() + "/read").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void markingTheSameOneReadTwiceIsHarmless() throws Exception {
        Notification a = give(ashaId, "a");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/notifications/" + a.getId() + "/read").header("Authorization", bearer(ashaToken)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void youCannotReadSomeoneElsesNotification() throws Exception {
        Notification ravis = give(raviId, "private");

        mvc.perform(post("/api/notifications/" + ravis.getId() + "/read").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isNotFound());

        assertThat(notifications.findById(ravis.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void anIdThatIsNotAUuidIs400() throws Exception {
        mvc.perform(post("/api/notifications/nope/read").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readAllClearsOnlyYourOwn() throws Exception {
        give(ashaId, "a");
        give(ashaId, "b");
        give(raviId, "c");

        mvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(ashaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/notifications").header("Authorization", bearer(raviToken)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void openingTheAppMakesAStudentReachableForNewDrives() throws Exception {
        // before asha has ever called the API, a new drive does not reach her
        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken))).andExpect(status().isOk());

        handler.handle("{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"drive.created\",\"payload\":{"
                + "\"driveId\":\"" + UUID.randomUUID() + "\",\"company\":\"Acme\",\"roleTitle\":\"SDE\","
                + "\"minCgpa\":7,\"deadline\":\"2026-11-05\",\"createdBy\":\"" + tprId + "\"}}");

        mvc.perform(get("/api/notifications").header("Authorization", bearer(ashaToken)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("New drive: Acme"));
        // a TPR who opens the app is not a student, so is not added to the student list
        mvc.perform(get("/api/notifications").header("Authorization", bearer(tprToken)))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
