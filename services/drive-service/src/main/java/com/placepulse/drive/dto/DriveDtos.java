package com.placepulse.drive.dto;

import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveApplication;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DriveDtos {

    private DriveDtos() {
    }

    public record CreateDriveRequest(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Size(max = 120) String roleTitle,
            @Size(max = 2000) String description,
            @Size(max = 120) String location,
            @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal packageLpa,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal minCgpa,
            /** Pass-out years that may apply, like [2026, 2027]. Empty or missing means every batch. */
            @Size(max = 10) List<@Min(2000) @Max(2100) Integer> eligibleBatches,
            @NotNull LocalDate deadline,
            /** Edit only: true deletes the current logo. */
            Boolean removeLogo) {
    }

    public record UpdateStatusRequest(@NotNull DriveStatus status) {
    }

    public record UpdateStageRequest(@NotNull Stage stage) {
    }

    /** Moves many applications to one stage at once. */
    public record BulkStageRequest(
            @NotNull @Size(min = 1, max = 200, message = "Choose between 1 and 200 applications") List<UUID> ids,
            @NotNull Stage stage) {
    }

    /**
     * `applicantCount` is only filled in for TPRs and `applied` only for students; the other is null.
     */
    public record DriveResponse(
            UUID id,
            String company,
            String roleTitle,
            String description,
            String location,
            BigDecimal packageLpa,
            BigDecimal minCgpa,
            List<Integer> eligibleBatches,
            String logoUrl,
            LocalDate deadline,
            DriveStatus status,
            boolean acceptingApplications,
            Long applicantCount,
            Boolean applied,
            Instant createdAt) {

        public static DriveResponse of(Drive d, LocalDate today, Long applicantCount, Boolean applied) {
            return new DriveResponse(d.getId(), d.getCompany(), d.getRoleTitle(), d.getDescription(),
                    d.getLocation(), d.getPackageLpa(), d.getMinCgpa(), new ArrayList<>(d.getEligibleBatches()), DriveDtos.logoPath(d), d.getDeadline(),
                    d.getStatus(), d.isAccepting(today), applicantCount, applied, d.getCreatedAt());
        }
    }

    /** Relative to this service. The version in the query string changes whenever the logo does. */
    static String logoPath(Drive d) {
        return d.getLogoUpdatedAt() == null ? null
                : "/api/drives/" + d.getId() + "/logo?v=" + d.getLogoUpdatedAt().toEpochMilli();
    }

    public record ApplicationResponse(
            UUID id,
            UUID driveId,
            String company,
            String roleTitle,
            UUID studentId,
            String studentName,
            String studentEmail,
            BigDecimal cgpa,
            String course,
            String batch,
            Stage stage,
            Stage debarredFrom,
            Instant appliedAt,
            Instant updatedAt) {

        public static ApplicationResponse of(DriveApplication a, Drive drive) {
            return new ApplicationResponse(a.getId(), a.getDriveId(),
                    drive == null ? null : drive.getCompany(),
                    drive == null ? null : drive.getRoleTitle(),
                    a.getStudentId(), a.getStudentName(), a.getStudentEmail(), a.getCgpa(), a.getCourse(), a.getBatch(),
                    a.getStage(), a.getDebarredFrom(), a.getAppliedAt(), a.getUpdatedAt());
        }
    }
}
