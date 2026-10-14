package com.placepulse.drive.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The events drive-service publishes. Each one carries everything a consumer needs, so a consumer
 * never has to call back into this service. Field names are a contract: add fields, don't rename them.
 */
public final class DriveEvents {

    public static final String DRIVE_CREATED = "drive.created";
    public static final String APPLICATION_SUBMITTED = "application.submitted";
    public static final String STAGE_CHANGED = "application.stage-changed";
    public static final String DRIVE_UPDATED = "drive.updated";
    public static final String APPLICATION_REMOVED = "application.removed";

    private DriveEvents() {
    }

    /** Every message on the topic is wrapped in this, so consumers can dedupe and route by type. */
    public record Envelope(UUID eventId, String type, Instant occurredAt, Object payload) {
    }

    public record DriveCreated(UUID driveId, String company, String roleTitle, BigDecimal minCgpa,
                               List<Integer> eligibleBatches, LocalDate deadline, UUID createdBy) {
    }

    /** `changes` are plain-words names of what was edited, like "deadline" or "minimum CGPA". */
    public record DriveUpdated(UUID driveId, String company, String roleTitle, List<String> changes, UUID createdBy) {
    }

    public record ApplicationRemoved(UUID applicationId, UUID driveId, String company, String roleTitle,
                                     UUID studentId, String studentName) {
    }

    public record ApplicationSubmitted(UUID applicationId, UUID driveId, String company, String roleTitle,
                                       UUID studentId, String studentName, UUID driveCreatedBy) {
    }

    public record StageChanged(UUID applicationId, UUID driveId, String company, String roleTitle,
                               UUID studentId, String studentName, String fromStage, String toStage) {
    }
}
