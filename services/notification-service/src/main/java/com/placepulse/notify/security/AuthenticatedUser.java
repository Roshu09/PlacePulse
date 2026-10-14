package com.placepulse.notify.security;

import java.util.UUID;

/** The principal placed in the SecurityContext after a valid JWT is verified. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
