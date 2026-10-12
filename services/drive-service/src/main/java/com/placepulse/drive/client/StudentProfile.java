package com.placepulse.drive.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** The part of auth-service's user record that this service needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentProfile(UUID id, String email, String fullName, String role,
                             BigDecimal cgpa, String course, String batch) {
}
