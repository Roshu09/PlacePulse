package com.placepulse.auth.dto;

import com.placepulse.auth.domain.Role;
import com.placepulse.auth.domain.ProfileData;
import com.placepulse.auth.domain.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * Replaces the whole profile: a null field clears that value. {@code acknowledgeDebar} must be true to edit a
     * profile that is already registered for placements.
     */
    public record UpdateProfileRequest(
            @Pattern(regexp = "^$|^[+0-9][0-9 \\-]{6,18}$", message = "Enter a valid phone number")
            String phone,
            @Size(max = 60) String course,
            @Size(max = 20) String batch,
            @DecimalMin(value = "0.0", message = "CGPA must be between 0 and 10")
            @DecimalMax(value = "10.0", message = "CGPA must be between 0 and 10")
            @Digits(integer = 2, fraction = 2, message = "CGPA can have at most 2 decimal places")
            BigDecimal cgpa,
            @DecimalMin(value = "0.0", message = "Must be between 0 and 100")
            @DecimalMax(value = "100.0", message = "Must be between 0 and 100")
            @Digits(integer = 3, fraction = 2, message = "At most 2 decimal places")
            BigDecimal tenthPercent,
            @DecimalMin(value = "0.0", message = "Must be between 0 and 100")
            @DecimalMax(value = "100.0", message = "Must be between 0 and 100")
            @Digits(integer = 3, fraction = 2, message = "At most 2 decimal places")
            BigDecimal twelfthPercent,
            @Size(max = 300) @Pattern(regexp = "^$|^https?://.+", message = "Enter a link starting with http:// or https://")
            String resumeUrl,
            @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
            @Size(max = 20) String gender,
            @Size(max = 80) String city,
            @Size(max = 60) String graduationDegree,
            @DecimalMin(value = "0.0", message = "Must be between 0 and 100")
            @DecimalMax(value = "100.0", message = "Must be between 0 and 100")
            @Digits(integer = 3, fraction = 2, message = "At most 2 decimal places")
            BigDecimal graduationPercent,
            @Size(max = 120) String graduationUniversity,
            @Min(value = 0, message = "Cannot be negative") @Max(value = 50, message = "Too many") Integer activeBacklogs,
            @Size(max = 300) @Pattern(regexp = "^$|^https?://.+", message = "Enter a link starting with http:// or https://")
            String githubUrl,
            @Size(max = 300) @Pattern(regexp = "^$|^https?://.+", message = "Enter a link starting with http:// or https://")
            String linkedinUrl,
            @Size(max = 300) @Pattern(regexp = "^$|^https?://.+", message = "Enter a link starting with http:// or https://")
            String portfolioUrl,
            @Size(max = 500) String skills,
            @Size(max = 1500) String projects,
            @Size(max = 1000) String certifications,
            boolean acknowledgeDebar) {

        /** The request as profile data: blank text becomes null, decimals get a fixed scale so equals() is reliable. */
        public ProfileData toProfile() {
            return new ProfileData(
                    clean(phone), clean(course), clean(batch), scale(cgpa), scale(tenthPercent), scale(twelfthPercent),
                    clean(resumeUrl), dateOfBirth, clean(gender), clean(city), clean(graduationDegree),
                    scale(graduationPercent), clean(graduationUniversity), activeBacklogs, clean(githubUrl),
                    clean(linkedinUrl), clean(portfolioUrl), clean(skills), clean(projects), clean(certifications));
        }

        private static String clean(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }

        private static BigDecimal scale(BigDecimal v) {
            return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    public record UserResponse(UUID id, String email, String fullName, Role role,
                               BigDecimal cgpa, String course, String batch,
                               String phone, BigDecimal tenthPercent, BigDecimal twelfthPercent, String resumeUrl,
                               LocalDate dateOfBirth, String gender, String city, String graduationDegree,
                               BigDecimal graduationPercent, String graduationUniversity, Integer activeBacklogs,
                               String githubUrl, String linkedinUrl, String portfolioUrl,
                               String skills, String projects, String certifications,
                               boolean placementRegistered, Instant placementRegisteredAt,
                               List<String> missingRequired) {
        public static UserResponse from(User user) {
            ProfileData p = user.profile();
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    p.cgpa(), p.course(), p.batch(),
                    p.phone(), p.tenthPercent(), p.twelfthPercent(), p.resumeUrl(),
                    p.dateOfBirth(), p.gender(), p.city(), p.graduationDegree(),
                    p.graduationPercent(), p.graduationUniversity(), p.activeBacklogs(),
                    p.githubUrl(), p.linkedinUrl(), p.portfolioUrl(),
                    p.skills(), p.projects(), p.certifications(),
                    user.isPlacementRegistered(), user.getPlacementRegisteredAt(),
                    user.getRole() == Role.STUDENT ? p.missingRequired() : List.of());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
