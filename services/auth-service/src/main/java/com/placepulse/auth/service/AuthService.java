package com.placepulse.auth.service;

import com.placepulse.auth.domain.Role;
import com.placepulse.auth.domain.User;
import com.placepulse.auth.dto.AuthDtos.AuthResponse;
import com.placepulse.auth.dto.AuthDtos.LoginRequest;
import com.placepulse.auth.dto.AuthDtos.RegisterRequest;
import com.placepulse.auth.dto.AuthDtos.UpdateProfileRequest;
import com.placepulse.auth.dto.AuthDtos.UserResponse;
import com.placepulse.auth.domain.ProfileData;
import com.placepulse.auth.events.AuthEvents;
import com.placepulse.auth.events.EventPublisher;
import com.placepulse.auth.exception.ApiException;
import com.placepulse.auth.repository.UserRepository;
import com.placepulse.auth.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EventPublisher events;
    private final String tprInviteCode;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EventPublisher events,
                       @Value("${app.tpr.invite-code:}") String tprInviteCode) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.events = events;
        this.tprInviteCode = tprInviteCode;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                resolveRole(request.inviteCode()));
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // two sign-ups with the same email racing past existsByEmail
            throw emailTaken();
        }
        if (user.getRole() == Role.STUDENT) {
            // lets notification-service reach this student before they ever open the app
            events.publish(user.getId().toString(), AuthEvents.STUDENT_REGISTERED,
                    new AuthEvents.StudentRegistered(user.getId(), user.getFullName(), user.getEmail()));
        }
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(user);
    }

    public UserResponse me(java.util.UUID id) {
        return users.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    /**
     * Saves the profile. Before registering for placements it is a free-form draft. After registering, a change
     * must be acknowledged (it debars the student from drives they already applied to) and must stay complete.
     */
    public UserResponse updateProfile(java.util.UUID id, UpdateProfileRequest request) {
        User user = findStudent(id);
        ProfileData next = request.toProfile();
        if (next.equals(user.profile())) {
            return UserResponse.from(user);
        }
        if (user.isPlacementRegistered()) {
            if (!request.acknowledgeDebar()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Editing a registered profile debars you from drives you already applied to. "
                                + "Confirm to continue.");
            }
            requireComplete(next);
        }
        user.applyProfile(next);
        user = users.saveAndFlush(user);
        if (user.isPlacementRegistered()) {
            events.publish(user.getId().toString(), AuthEvents.PROFILE_EDITED,
                    new AuthEvents.ProfileEdited(user.getId(), user.getFullName(), Instant.now()));
        }
        return UserResponse.from(user);
    }

    /** Locks the profile in. Needs every required field; calling it again changes nothing. */
    public UserResponse registerForPlacements(java.util.UUID id) {
        User user = findStudent(id);
        if (user.isPlacementRegistered()) {
            return UserResponse.from(user);
        }
        requireComplete(user.profile());
        user.registerForPlacements();
        user = users.saveAndFlush(user);
        events.publish(user.getId().toString(), AuthEvents.STUDENT_REGISTERED,
                new AuthEvents.StudentRegistered(user.getId(), user.getFullName(), user.getEmail()));
        return UserResponse.from(user);
    }

    private User findStudent(java.util.UUID id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        if (user.getRole() != Role.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only students have a placement profile");
        }
        return user;
    }

    private static void requireComplete(ProfileData profile) {
        List<String> missing = profile.missingRequired();
        if (!missing.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            missing.forEach(field -> errors.put(field, "Required"));
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Fill in all required fields first", errors);
        }
    }

    /** Everyone is a STUDENT unless they present the TPR invite code. */
    private Role resolveRole(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return Role.STUDENT;
        }
        boolean enabled = !tprInviteCode.isBlank();
        boolean matches = enabled && MessageDigest.isEqual(
                inviteCode.getBytes(StandardCharsets.UTF_8),
                tprInviteCode.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invalid invite code");
        }
        return Role.TPR;
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "Email already registered");
    }
}
