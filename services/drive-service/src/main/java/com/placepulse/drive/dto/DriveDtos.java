package com.placepulse.drive.dto;

import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveApplication;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
            @Size(max = 20) String eligibleBatch,
            @NotNull LocalDate deadline) {
    }

    public record UpdateStatusRequest(@NotNull DriveStatus status) {
    }

    public record UpdateStageRequest(@NotNull Stage stage) {
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
            String eligibleBatch,
            LocalDate deadline,
            DriveStatus status,
            boolean acceptingApplications,
            Long applicantCount,
            Boolean applied,
            Instant createdAt) {

        public static DriveResponse of(Drive d, LocalDate today, Long applicantCount, Boolean applied) {
            return new DriveResponse(d.getId(), d.getCompany(), d.getRoleTitle(), d.getDescription(),
                    d.getLocation(), d.getPackageLpa(), d.getMinCgpa(), d.getEligibleBatch(), d.getDeadline(),
                    d.getStatus(), d.isAccepting(today), applicantCount, applied, d.getCreatedAt());
        }
    }

    public record ApplicationResponse(
            UUID id,
            UUID driveId,
            String company,
            String roleTitle,
            String studentName,
            String studentEmail,
            BigDecimal cgpa,
            String course,
            String batch,
            Stage stage,
            Instant appliedAt,
            Instant updatedAt) {

        public static ApplicationResponse of(DriveApplication a, Drive drive) {
            return new ApplicationResponse(a.getId(), a.getDriveId(),
                    drive == null ? null : drive.getCompany(),
                    drive == null ? null : drive.getRoleTitle(),
                    a.getStudentName(), a.getStudentEmail(), a.getCgpa(), a.getCourse(), a.getBatch(),
                    a.getStage(), a.getAppliedAt(), a.getUpdatedAt());
        }
    }
}
