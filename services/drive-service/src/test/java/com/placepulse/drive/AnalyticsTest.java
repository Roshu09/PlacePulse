package com.placepulse.drive;

import com.placepulse.drive.analytics.AnalyticsCache;
import com.placepulse.drive.client.ProfileClient;
import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveApplication;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import com.placepulse.drive.events.EventPublisher;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsTest {

    /** A real in-memory cache, so hit / miss / invalidate behaviour is tested without Redis. */
    @TestConfiguration
    static class CacheConfig {
        @Bean
        @Primary
        FakeCache fakeCache() {
            return new FakeCache();
        }
    }

    static class FakeCache implements AnalyticsCache {
        final AtomicReference<String> value = new AtomicReference<>();
        boolean broken;

        @Override public Optional<String> get() {
            if (broken) throw new IllegalStateException("redis down");
            return Optional.ofNullable(value.get());
        }
        @Override public void put(String json) {
            if (broken) throw new IllegalStateException("redis down");
            value.set(json);
        }
        @Override public void invalidate() {
            value.set(null);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired DriveRepository drives;
    @Autowired DriveApplicationRepository applications;
    @Autowired FakeCache cache;
    @Value("${app.jwt.secret}") String jwtSecret;

    @MockBean ProfileClient profileClient;
    @MockBean EventPublisher events;

    private String tpr;
    private String student;

    @BeforeEach
    void setUp() {
        applications.deleteAll();
        drives.deleteAll();
        cache.value.set(null);
        cache.broken = false;
        tpr = token(Role.TPR);
        student = token(Role.STUDENT);
    }

    private String token(Role role) {
        return "Bearer " + Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("email", role + "@example.com").claim("role", role.name())
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Drive drive(String company, String lpa) {
        return drives.save(new Drive(company, "SDE", null, null, new BigDecimal(lpa), new BigDecimal("7.0"),
                Set.of(), LocalDate.now().plusDays(5), UUID.randomUUID()));
    }

    private UUID app(Drive d, UUID studentId, String cgpa, Stage stage) {
        DriveApplication a = new DriveApplication(d.getId(), studentId, "S", "s@x.com", new BigDecimal(cgpa), "MCA", "2027");
        a.moveTo(stage);
        return applications.save(a).getId();
    }

    @Test
    void onlyTprsCanSeeAnalytics() throws Exception {
        mvc.perform(get("/api/analytics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics").header("Authorization", student)).andExpect(status().isForbidden());
    }

    @Test
    void emptyDatabaseGivesZerosNotErrors() throws Exception {
        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.drives").value(0))
                .andExpect(jsonPath("$.totals.placementRate").value(0.0))
                .andExpect(jsonPath("$.totals.averagePackageLpa").doesNotExist())
                .andExpect(jsonPath("$.timeline.length()").value(14));
    }

    @Test
    void numbersAddUp() throws Exception {
        Drive acme = drive("Acme", "10.0");
        Drive globex = drive("Globex", "20.0");
        Drive closed = drive("Initech", "5.0");
        closed.setStatus(DriveStatus.CLOSED);
        drives.save(closed);

        UUID asha = UUID.randomUUID(), ravi = UUID.randomUUID(), meera = UUID.randomUUID();
        app(acme, asha, "9.0", Stage.OFFER);
        app(globex, asha, "9.0", Stage.INTERVIEW);   // same student, second drive
        app(acme, ravi, "7.0", Stage.REJECTED);
        app(globex, ravi, "7.0", Stage.OFFER);
        app(acme, meera, "8.0", Stage.APPLIED);

        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.drives").value(3))
                .andExpect(jsonPath("$.totals.openDrives").value(2))
                .andExpect(jsonPath("$.totals.applications").value(5))
                .andExpect(jsonPath("$.totals.studentsApplied").value(3))
                .andExpect(jsonPath("$.totals.studentsPlaced").value(2))
                .andExpect(jsonPath("$.totals.offers").value(2))
                .andExpect(jsonPath("$.totals.placementRate").value(66.7))
                .andExpect(jsonPath("$.totals.averageCgpaPlaced").value(8.0))
                // one 10 LPA offer and one 20 LPA offer
                .andExpect(jsonPath("$.totals.averagePackageLpa").value(15.0))
                .andExpect(jsonPath("$.totals.highestPackageLpa").value(20.0))
                .andExpect(jsonPath("$.funnel.OFFER").value(2))
                .andExpect(jsonPath("$.funnel.REJECTED").value(1))
                .andExpect(jsonPath("$.funnel.APPLIED").value(1))
                // Acme has 3 applicants and is listed first
                .andExpect(jsonPath("$.drives[0].company").value("Acme"))
                .andExpect(jsonPath("$.drives[0].applicants").value(3))
                .andExpect(jsonPath("$.drives[0].offers").value(1))
                .andExpect(jsonPath("$.drives[0].conversion").value(33.3))
                // today's five applications land in the last bucket
                .andExpect(jsonPath("$.timeline[13].applications").value(5));
    }

    @Test
    void secondRequestIsServedFromCacheAndWritesClearIt() throws Exception {
        Drive acme = drive("Acme", "10.0");
        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(jsonPath("$.totals.applications").value(0));
        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(header().string("X-Cache", "HIT"));

        // a status change goes through DriveService, which must clear the cache
        mvc.perform(MockMvcRequestBuilders.patch("/api/drives/" + acme.getId() + "/status")
                        .header("Authorization", tpr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());
        assertThat(cache.value.get()).isNull();
        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(jsonPath("$.totals.openDrives").value(0));
    }

    @Test
    void applyingClearsTheCache() throws Exception {
        Drive acme = drive("Acme", "10.0");
        when(profileClient.fetch(anyString())).thenReturn(new StudentProfile(
                UUID.randomUUID(), "a@x.com", "Asha", "STUDENT", new BigDecimal("8.5"), "MCA", "2027", true));
        mvc.perform(get("/api/analytics").header("Authorization", tpr)).andExpect(status().isOk());
        assertThat(cache.value.get()).isNotNull();

        mvc.perform(MockMvcRequestBuilders.post("/api/drives/" + acme.getId() + "/apply")
                .header("Authorization", student)).andExpect(status().isCreated());

        assertThat(cache.value.get()).isNull();
        mvc.perform(get("/api/analytics").header("Authorization", tpr))
                .andExpect(jsonPath("$.totals.applications").value(1));
    }

    @Test
    void aBrokenCacheNeverBreaksTheDashboard() throws Exception {
        drive("Acme", "10.0");
        cache.broken = true;
        // the fake throws like a dead Redis would; the service must not let that reach the user
        mvc.perform(get("/api/analytics").header("Authorization", tpr)).andExpect(status().isOk());
    }

    @Test
    void nocacheSkipsRedisAndDoesNotStoreAnything() throws Exception {
        drive("Acme", "10.0");
        mvc.perform(get("/api/analytics?nocache=true").header("Authorization", tpr))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "MISS"));
        assertThat(cache.value.get()).isNull();

        mvc.perform(get("/api/analytics").header("Authorization", tpr)).andExpect(header().string("X-Cache", "MISS"));
        // even with a warm cache, nocache must recompute
        mvc.perform(get("/api/analytics?nocache=true").header("Authorization", tpr))
                .andExpect(header().string("X-Cache", "MISS"));
    }
}
