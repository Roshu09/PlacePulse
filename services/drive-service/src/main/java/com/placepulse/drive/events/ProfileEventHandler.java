package com.placepulse.drive.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placepulse.drive.service.DriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads events published by auth-service. Only profile.edited matters here; everything else on the topic
 * (this service's own events included) is ignored.
 */
@Component
public class ProfileEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProfileEventHandler.class);

    static final String PROFILE_EDITED = "profile.edited";

    private final ObjectMapper mapper;
    private final DriveService driveService;

    public ProfileEventHandler(ObjectMapper mapper, DriveService driveService) {
        this.mapper = mapper;
        this.driveService = driveService;
    }

    /** Throws only for a message that can never work (bad JSON) or a database problem worth retrying. */
    public void handle(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unreadable event: " + e.getOriginalMessage(), e);
        }
        if (root == null || !PROFILE_EDITED.equals(root.path("type").asText())) {
            return;
        }
        String studentId = root.path("payload").path("studentId").asText("");
        UUID id;
        try {
            id = UUID.fromString(studentId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("profile.edited without a valid studentId", e);
        }
        int debarred = driveService.debarStudent(id);
        log.info("Profile edited by {}: {} application(s) debarred", id, debarred);
    }
}
