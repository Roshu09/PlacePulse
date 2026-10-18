package com.placepulse.drive.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placepulse.drive.analytics.AnalyticsDtos.Analytics;
import com.placepulse.drive.analytics.AnalyticsDtos.DayCount;
import com.placepulse.drive.analytics.AnalyticsDtos.DriveStat;
import com.placepulse.drive.analytics.AnalyticsDtos.Totals;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import com.placepulse.drive.repository.DriveApplicationRepository;
import com.placepulse.drive.repository.DriveApplicationRepository.DriveStageCount;
import com.placepulse.drive.repository.DriveRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalyticsService.class);

    static final int TIMELINE_DAYS = 14;

    /** The JSON plus how it was produced, so the API can report cache hits and timings. */
    public record Result(String json, boolean cacheHit, long millis) {
    }

    private final DriveRepository drives;
    private final DriveApplicationRepository applications;
    private final AnalyticsCache cache;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AnalyticsService(DriveRepository drives, DriveApplicationRepository applications,
                            AnalyticsCache cache, ObjectMapper mapper, Clock clock) {
        this.drives = drives;
        this.applications = applications;
        this.cache = cache;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Cached answer if there is one, otherwise computed from the database and stored. */
    public Result get() {
        return get(false);
    }

    /** `bypass` skips Redis completely (no read, no write): the honest "without cache" path for benchmarks. */
    public Result get(boolean bypass) {
        long start = System.nanoTime();
        if (bypass) {
            return new Result(toJson(compute()), false, elapsedMs(start));
        }
        try {
            var cached = cache.get();
            if (cached.isPresent()) {
                return new Result(cached.get(), true, elapsedMs(start));
            }
        } catch (RuntimeException e) {
            log.warn("Analytics cache read failed, using the database: {}", e.getMessage());
        }
        String json = toJson(compute());
        try {
            cache.put(json);
        } catch (RuntimeException e) {
            log.warn("Analytics cache write failed: {}", e.getMessage());
        }
        return new Result(json, false, elapsedMs(start));
    }

    /** Called after every change to drives or applications. */
    public void invalidate() {
        try {
            cache.invalidate();
        } catch (RuntimeException e) {
            log.warn("Analytics cache invalidate failed: {}", e.getMessage());
        }
    }

    public Analytics compute() {
        LocalDate today = LocalDate.now(clock);
        List<Drive> allDrives = drives.findAll();

        Map<UUID, Map<Stage, Long>> byDrive = new HashMap<>();
        Map<Stage, Long> funnelCounts = new EnumMap<>(Stage.class);
        for (DriveStageCount row : applications.countByDriveAndStage()) {
            byDrive.computeIfAbsent(row.getDriveId(), k -> new EnumMap<>(Stage.class)).put(row.getStage(), row.getTotal());
            funnelCounts.merge(row.getStage(), row.getTotal(), Long::sum);
        }

        Map<String, Long> funnel = new LinkedHashMap<>();
        for (Stage s : Stage.values()) {
            funnel.put(s.name(), funnelCounts.getOrDefault(s, 0L));
        }
        long totalApplications = funnelCounts.values().stream().mapToLong(Long::longValue).sum();

        List<DriveStat> stats = new ArrayList<>();
        double offerValue = 0;
        long offersWithPackage = 0;
        BigDecimal highest = null;
        for (Drive d : allDrives) {
            Map<Stage, Long> c = byDrive.getOrDefault(d.getId(), Map.of());
            long applicants = c.values().stream().mapToLong(Long::longValue).sum();
            long offers = c.getOrDefault(Stage.OFFER, 0L);
            // "shortlisted" means made it past the first screen and is still alive
            long shortlisted = c.getOrDefault(Stage.SHORTLISTED, 0L) + c.getOrDefault(Stage.TEST, 0L)
                    + c.getOrDefault(Stage.INTERVIEW, 0L) + offers;
            stats.add(new DriveStat(d.getId(), d.getCompany(), d.getRoleTitle(), d.getStatus().name(),
                    d.getDeadline(), applicants, shortlisted, offers, percent(offers, applicants)));
            if (offers > 0 && d.getPackageLpa() != null) {
                offerValue += d.getPackageLpa().doubleValue() * offers;
                offersWithPackage += offers;
                if (highest == null || d.getPackageLpa().compareTo(highest) > 0) {
                    highest = d.getPackageLpa();
                }
            }
        }
        stats.sort(Comparator.comparingLong(DriveStat::applicants).reversed()
                .thenComparing(DriveStat::company, String.CASE_INSENSITIVE_ORDER));

        int open = (int) allDrives.stream()
                .filter(d -> d.getStatus() == DriveStatus.OPEN && !d.getDeadline().isBefore(today)).count();
        long studentsApplied = applications.countDistinctStudents();
        long studentsPlaced = applications.countDistinctStudentsAtStage(Stage.OFFER);

        Totals totals = new Totals(allDrives.size(), open, totalApplications, studentsApplied, studentsPlaced,
                funnelCounts.getOrDefault(Stage.OFFER, 0L), percent(studentsPlaced, studentsApplied),
                round(applications.averageCgpa()), round(applications.averageCgpaAtStage(Stage.OFFER)),
                offersWithPackage == 0 ? null : round(offerValue / offersWithPackage),
                highest == null ? null : highest.doubleValue());

        return new Analytics(totals, funnel, stats, timeline(today), Instant.now(clock));
    }

    private List<DayCount> timeline(LocalDate today) {
        LocalDate first = today.minusDays(TIMELINE_DAYS - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant at : applications.appliedAtSince(first.atStartOfDay(clock.getZone()).toInstant())) {
            counts.merge(at.atZone(clock.getZone()).toLocalDate(), 1L, Long::sum);
        }
        List<DayCount> out = new ArrayList<>();
        for (int i = 0; i < TIMELINE_DAYS; i++) {
            LocalDate day = first.plusDays(i);
            out.add(new DayCount(day, counts.getOrDefault(day, 0L)));
        }
        return out;
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : round(part * 100.0 / whole);
    }

    private static Double round(Double v) {
        return v == null ? null : round(v.doubleValue());
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String toJson(Analytics a) {
        try {
            return mapper.writeValueAsString(a);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise analytics", e);
        }
    }
}
