package com.placepulse.auth.dto;

import com.placepulse.auth.domain.Role;
import com.placepulse.auth.domain.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // bcrypt only uses the first 72 bytes, so cap the length
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String fullName,
            String inviteCode) {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    /** Replaces the whole profile: a null field clears that value. */
    public record UpdateProfileRequest(
            @DecimalMin(value = "0.0", message = "CGPA must be between 0 and 10")
            @DecimalMax(value = "10.0", message = "CGPA must be between 0 and 10")
            @Digits(integer = 2, fraction = 2, message = "CGPA can have at most 2 decimal places")
            BigDecimal cgpa,
            @Size(max = 60) String course,
            @Size(max = 20) String batch) {
    }

    public record UserResponse(UUID id, String email, String fullName, Role role,
                               BigDecimal cgpa, String course, String batch) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.getCgpa(), user.getCourse(), user.getBatch());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
