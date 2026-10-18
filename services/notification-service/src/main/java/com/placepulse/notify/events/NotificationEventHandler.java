package com.placepulse.notify.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placepulse.notify.domain.Notification;
import com.placepulse.notify.dto.NotificationDtos.NotificationResponse;
import com.placepulse.notify.repository.NotificationRepository;
import com.placepulse.notify.service.SseHub;
import com.placepulse.notify.service.StudentDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns one event from drive-service into notifications. It reads the JSON by field name rather than
 * sharing classes with drive-service, so the two services only agree on the message format.
 *
 * Safe to run twice for the same event: recipients who already have a notification for that eventId
 * are skipped (Kafka delivers at-least-once).
 */
@Service
public class NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    static final String DRIVE_CREATED = "drive.created";
    static final String APPLICATION_SUBMITTED = "application.submitted";
    static final String STAGE_CHANGED = "application.stage-changed";
    static final String DRIVE_UPDATED = "drive.updated";
    static final String APPLICATION_REMOVED = "application.removed";
    static final String STUDENT_REGISTERED = "student.registered";

    private static final Map<String, String> STAGE_LABEL = Map.of(
            "APPLIED", "Applied",
            "SHORTLISTED", "Shortlisted",
            "TEST", "Test",
            "INTERVIEW", "Interview",
            "OFFER", "Offer",
            "REJECTED", "Not selected",
            "DEBARRED", "Debarred");

    private final ObjectMapper mapper;
    private final NotificationRepository notifications;
    private final StudentDirectory directory;
    private final SseHub hub;

    public NotificationEventHandler(ObjectMapper mapper, NotificationRepository notifications,
                                    StudentDirectory directory, SseHub hub) {
        this.mapper = mapper;
        this.notifications = notifications;
        this.directory = directory;
        this.hub = hub;
    }

    public void handle(String json) {
        JsonNode root = parse(json);
        UUID eventId = uuid(root, "eventId");
        String type = text(root, "type");
        JsonNode payload = root.path("payload");

        switch (type) {
            case DRIVE_CREATED -> onDriveCreated(eventId, payload);
            case APPLICATION_SUBMITTED -> onApplicationSubmitted(eventId, payload);
            case STAGE_CHANGED -> onStageChanged(eventId, payload);
            case DRIVE_UPDATED -> onDriveUpdated(eventId, payload);
            case APPLICATION_REMOVED -> onApplicationRemoved(eventId, payload);
            case STUDENT_REGISTERED -> directory.remember(uuid(payload, "studentId"));
            // a newer drive-service may publish types this version does not know yet: skip, don't fail
            default -> log.debug("Ignoring event type {}", type);
        }
    }

    // ---- one method per event type ---------------------------------------------------------

    private void onDriveCreated(UUID eventId, JsonNode p) {
        String company = text(p, "company");
        StringBuilder body = new StringBuilder(text(p, "roleTitle"))
                .append(" · CGPA ").append(cgpa(p, "minCgpa")).append("+");
        String batch = batches(p);
        if (batch != null) {
            body.append(" · batch ").append(batch);
        }
        body.append(" · apply by ").append(date(p, "deadline"));

        deliver(eventId, DRIVE_CREATED, directory.allIds(), "New drive: " + company, body.toString(), "/drives");
    }

    private void onDriveUpdated(UUID eventId, JsonNode p) {
        String company = text(p, "company");
        List<String> changes = new java.util.ArrayList<>();
        p.path("changes").forEach(c -> changes.add(c.asText()));
        String what = changes.isEmpty() ? "details" : String.join(", ", changes);
        deliver(eventId, DRIVE_UPDATED, directory.allIds(), "Drive updated: " + company,
                text(p, "roleTitle") + " · changed: " + what, "/drives");
    }

    private void onApplicationRemoved(UUID eventId, JsonNode p) {
        UUID studentId = uuid(p, "studentId");
        directory.remember(studentId);
        deliver(eventId, APPLICATION_REMOVED, List.of(studentId),
                "Your " + text(p, "company") + " application was removed",
                text(p, "roleTitle") + ": your placement team removed it. You can apply again if the drive is still open.",
                "/drives");
    }

    /** "2026, 2027" from the list a current drive-service sends, or the single text an older one sent. */
    private static String batches(JsonNode p) {
        JsonNode list = p.get("eligibleBatches");
        if (list != null && list.isArray() && !list.isEmpty()) {
            List<String> years = new java.util.ArrayList<>();
            list.forEach(y -> years.add(y.asText()));
            return String.join(", ", years);
        }
        return optional(p, "eligibleBatch");
    }

    private void onApplicationSubmitted(UUID eventId, JsonNode p) {
        // an applicant is certainly a student, so this also teaches us about them
        directory.remember(uuid(p, "studentId"));

        // the TPR who posted the drive, and the rest of the team too
        UUID owner = uuid(p, "driveCreatedBy");
        directory.rememberTpr(owner);
        deliver(eventId, APPLICATION_SUBMITTED, teamWith(owner),
                "New application for " + text(p, "company"),
                text(p, "studentName") + " applied for " + text(p, "roleTitle"),
                "/drives/" + uuid(p, "driveId"));
    }

    private void onStageChanged(UUID eventId, JsonNode p) {
        UUID studentId = uuid(p, "studentId");
        directory.remember(studentId);

        String company = text(p, "company");
        String to = text(p, "toStage");
        String title = switch (to) {
            case "SHORTLISTED" -> "You're shortlisted at " + company;
            case "TEST" -> "Test round at " + company;
            case "INTERVIEW" -> "Interview round at " + company;
            case "OFFER" -> "Offer from " + company;
            case "REJECTED" -> "Update on your " + company + " application";
            case "DEBARRED" -> "You were debarred from " + company;
            default -> "Your " + company + " application moved to " + label(to);
        };
        String body = "DEBARRED".equals(to)
                ? text(p, "roleTitle") + ": you edited your profile, so this application is on hold. The placement team can reinstate it."
                : text(p, "roleTitle") + ": moved from " + label(text(p, "fromStage")) + " to " + label(to);

        deliver(eventId, STAGE_CHANGED, List.of(studentId), title, body, "/applications");

        if ("DEBARRED".equals(to)) {
            // a debar is something the placement team may need to act on (reinstate), so every TPR hears about it
            deliver(eventId, STAGE_CHANGED, directory.allTprIds(),
                    text(p, "studentName") + " was debarred from " + company,
                    text(p, "roleTitle") + ": they edited their profile. Reinstate them from the applicants page if that is fine.",
                    "/drives/" + uuid(p, "driveId"));
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Every known TPR, plus the given one even if this service has not met them yet. */
    private List<UUID> teamWith(UUID owner) {
        Set<UUID> team = new java.util.LinkedHashSet<>(directory.allTprIds());
        team.add(owner);
        return List.copyOf(team);
    }

    private void deliver(UUID eventId, String type, Collection<UUID> recipients, String title, String body, String link) {
        if (recipients.isEmpty()) {
            return;
        }
        Set<UUID> alreadyTold = new HashSet<>(notifications.findRecipientIdsByEventId(eventId));
        List<Notification> fresh = recipients.stream()
                .distinct()
                .filter(r -> !alreadyTold.contains(r))
                .map(r -> new Notification(eventId, r, type, clip(title, 160), clip(body, 400), link))
                .toList();
        List<Notification> saved = notifications.saveAll(fresh);
        // push only after the rows are stored, so a refresh triggered by the push always sees them
        saved.forEach(n -> hub.push(n.getRecipientId(), NotificationResponse.of(n)));
        log.info("Event {} ({}): {} new notification(s), {} already delivered", eventId, type, fresh.size(), alreadyTold.size());
    }

    private JsonNode parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new InvalidEventException("Event is not a JSON object");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Event is not valid JSON", e);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = optional(node, field);
        if (value == null) {
            throw new InvalidEventException("Event is missing '" + field + "'");
        }
        return value;
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("'" + field + "' is not a valid id", e);
        }
    }

    private static String cgpa(JsonNode node, String field) {
        try {
            return new BigDecimal(text(node, field)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            throw new InvalidEventException("'" + field + "' is not a number", e);
        }
    }

    /** "2026-11-05" becomes "5 Nov 2026"; anything else is shown as received. */
    private static String date(JsonNode node, String field) {
        String raw = text(node, field);
        try {
            return LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    private static String label(String stage) {
        return STAGE_LABEL.getOrDefault(stage, stage);
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
