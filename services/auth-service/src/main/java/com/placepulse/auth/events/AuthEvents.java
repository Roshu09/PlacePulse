package com.placepulse.auth.events;

import java.time.Instant;
import java.util.UUID;

/** The events auth-service publishes. Field names are a contract: add fields, don't rename them. */
public final class AuthEvents {

    public static final String STUDENT_REGISTERED = "student.registered";
    public static final String PROFILE_EDITED = "profile.edited";

    private AuthEvents() {
    }

    /** Every message on the topic is wrapped in this, so consumers can dedupe and route by type. */
    public record Envelope(UUID eventId, String type, Instant occurredAt, Object payload) {
    }

    /** A student confirmed their profile and registered for placements. */
    public record StudentRegistered(UUID studentId, String studentName, String email) {
    }

    /** A student who had already registered changed their profile (and accepted the consequences). */
    public record ProfileEdited(UUID studentId, String studentName, Instant editedAt) {
    }
}
