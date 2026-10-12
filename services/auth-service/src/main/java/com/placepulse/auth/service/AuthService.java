package com.placepulse.auth.service;

import com.placepulse.auth.domain.Role;
import com.placepulse.auth.domain.User;
import com.placepulse.auth.dto.AuthDtos.AuthResponse;
import com.placepulse.auth.dto.AuthDtos.LoginRequest;
import com.placepulse.auth.dto.AuthDtos.RegisterRequest;
import com.placepulse.auth.dto.AuthDtos.UpdateProfileRequest;
import com.placepulse.auth.dto.AuthDtos.UserResponse;
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
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String tprInviteCode;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.tpr.invite-code:}") String tprInviteCode) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    public UserResponse updateProfile(java.util.UUID id, UpdateProfileRequest request) {
        User user = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        user.updateProfile(request.cgpa(), blankToNull(request.course()), blankToNull(request.batch()));
        return UserResponse.from(users.saveAndFlush(user));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
