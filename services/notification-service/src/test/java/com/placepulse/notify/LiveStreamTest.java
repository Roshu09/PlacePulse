package com.placepulse.notify;

import com.placepulse.notify.dto.NotificationDtos.NotificationResponse;
import com.placepulse.notify.events.NotificationEventHandler;
import com.placepulse.notify.repository.KnownStudentRepository;
import com.placepulse.notify.repository.NotificationRepository;
import com.placepulse.notify.service.SseHub;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveStreamTest {

    @Autowired MockMvc mvc;
    @Autowired SseHub hub;
    @Autowired NotificationEventHandler handler;
    @Autowired NotificationRepository notifications;
    @Autowired KnownStudentRepository students;
    @Value("${app.jwt.secret}") String jwtSecret;

    private final UUID studentId = UUID.randomUUID();
    private final UUID otherStudentId = UUID.randomUUID();
    private final UUID tprId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        students.deleteAll();
    }

    private String bearer(UUID id, String role) {
        return "Bearer " + Jwts.builder()
                .subject(id.toString())
                .claim("email", id + "@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String driveCreated() {
        return """
                {"eventId":"%s","type":"drive.created","occurredAt":"2026-10-01T10:00:00Z",
                 "payload":{"company":"Acme","roleTitle":"SDE","minCgpa":7.5,"deadline":"2026-11-05"}}
                """.formatted(UUID.randomUUID());
    }

    @Test
    void streamNeedsAToken() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void anOpenStreamReceivesTheNotificationAsSoonAsItIsSaved() throws Exception {
        MvcResult stream = mvc.perform(get("/api/notifications/stream").header("Authorization", bearer(studentId, "STUDENT")))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(hub.connectionCount()).isGreaterThanOrEqualTo(1);

        handler.handle(driveCreated());

        String body = stream.getResponse().getContentAsString();
        assertThat(body).contains("event:ready");
        assertThat(body).contains("event:notification");
        assertThat(body).contains("New drive: Acme");
    }

    @Test
    void connectingMakesAStudentReachableForNewDrives() throws Exception {
        // never called GET /api/notifications, only opened the live stream
        mvc.perform(get("/api/notifications/stream").header("Authorization", bearer(studentId, "STUDENT")))
                .andExpect(request().asyncStarted());

        handler.handle(driveCreated());

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(studentId)).hasSize(1);
    }

    @Test
    void aStreamOnlyGetsItsOwnNotifications() throws Exception {
        MvcResult mine = mvc.perform(get("/api/notifications/stream").header("Authorization", bearer(studentId, "STUDENT")))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult theirs = mvc.perform(get("/api/notifications/stream").header("Authorization", bearer(tprId, "TPR")))
                .andExpect(request().asyncStarted()).andReturn();

        handler.handle(driveCreated()); // goes to students only

        assertThat(mine.getResponse().getContentAsString()).contains("New drive: Acme");
        assertThat(theirs.getResponse().getContentAsString()).doesNotContain("New drive");
    }

    @Test
    void pushWithNobodyListeningIsHarmless() {
        assertThatCode(() -> hub.push(otherStudentId, new NotificationResponse(UUID.randomUUID(), "x", "t", "b", "/", false, Instant.now()))).doesNotThrowAnyException();
    }
}
