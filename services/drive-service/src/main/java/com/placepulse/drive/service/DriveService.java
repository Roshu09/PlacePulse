package com.placepulse.drive.service;

import com.placepulse.drive.client.ProfileClient;
import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveApplication;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.CreateDriveRequest;
import com.placepulse.drive.dto.DriveDtos.DriveResponse;
import com.placepulse.drive.exception.ApiException;
import com.placepulse.drive.repository.DriveApplicationRepository;
import com.placepulse.drive.repository.DriveRepository;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DriveService {

    private final DriveRepository drives;
    private final DriveApplicationRepository applications;
    private final ProfileClient profileClient;
    private final Clock clock;

    public DriveService(DriveRepository drives,
                        DriveApplicationRepository applications,
                        ProfileClient profileClient,
                        Clock clock) {
        this.drives = drives;
        this.applications = applications;
        this.profileClient = profileClient;
        this.clock = clock;
    }

    // ---- drives ---------------------------------------------------------------------------

    public DriveResponse create(AuthenticatedUser tpr, CreateDriveRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.deadline().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("deadline", "The last date to apply can't be in the past."));
        }
        Drive drive = new Drive(
                request.company().trim(),
                request.roleTitle().trim(),
                blankToNull(request.description()),
                blankToNull(request.location()),
                request.packageLpa(),
                request.minCgpa(),
                blankToNull(request.eligibleBatch()),
                request.deadline(),
                tpr.id());
        return DriveResponse.of(drives.save(drive), today, 0L, null);
    }

    /** TPRs see every drive with its applicant count. Students see only drives they can still apply to. */
    public List<DriveResponse> list(AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        if (user.role() == Role.TPR) {
            List<Drive> all = drives.findAllByOrderByCreatedAtDesc();
            Map<UUID, Long> counts = applicantCounts(all.stream().map(Drive::getId).toList());
            return all.stream()
                    .map(d -> DriveResponse.of(d, today, counts.getOrDefault(d.getId(), 0L), null))
                    .toList();
        }
        List<Drive> open = drives.findByStatusAndDeadlineGreaterThanEqualOrderByDeadlineAsc(DriveStatus.OPEN, today);
        Set<UUID> appliedTo = appliedDriveIds(user.id());
        return open.stream()
                .map(d -> DriveResponse.of(d, today, null, appliedTo.contains(d.getId())))
                .toList();
    }

    public DriveResponse get(UUID id, AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        Drive drive = findDrive(id);
        if (user.role() == Role.TPR) {
            Long count = applicantCounts(List.of(id)).getOrDefault(id, 0L);
            return DriveResponse.of(drive, today, count, null);
        }
        return DriveResponse.of(drive, today, null, appliedDriveIds(user.id()).contains(id));
    }

    public DriveResponse setStatus(UUID id, DriveStatus status) {
        Drive drive = findDrive(id);
        drive.setStatus(status);
        Drive saved = drives.save(drive);
        Long count = applicantCounts(List.of(id)).getOrDefault(id, 0L);
        return DriveResponse.of(saved, LocalDate.now(clock), count, null);
    }

    // ---- applications ---------------------------------------------------------------------

    public ApplicationResponse apply(UUID driveId, AuthenticatedUser student, String authorizationHeader) {
        Drive drive = findDrive(driveId);

        if (!drive.isAccepting(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "This drive is no longer accepting applications.");
        }
        if (applications.existsByDriveIdAndStudentId(driveId, student.id())) {
            throw alreadyApplied();
        }

        // The profile lives in auth-service, so ask it, using the student's own token.
        StudentProfile profile = profileClient.fetch(authorizationHeader);
        EligibilityChecker.check(drive, profile).ifPresent(reason -> {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
        });

        DriveApplication application = new DriveApplication(
                driveId, student.id(), profile.fullName(), profile.email(),
                profile.cgpa(), profile.course(), profile.batch());
        try {
            application = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // two clicks racing past the exists check; the unique constraint catches the second
            throw alreadyApplied();
        }
        return ApplicationResponse.of(application, drive);
    }

    public List<ApplicationResponse> myApplications(AuthenticatedUser student) {
        List<DriveApplication> mine = applications.findAllByStudentIdOrderByAppliedAtDesc(student.id());
        Map<UUID, Drive> byId = drivesById(mine.stream().map(DriveApplication::getDriveId).distinct().toList());
        return mine.stream().map(a -> ApplicationResponse.of(a, byId.get(a.getDriveId()))).toList();
    }

    public List<ApplicationResponse> applicants(UUID driveId) {
        Drive drive = findDrive(driveId);
        return applications.findAllByDriveIdOrderByAppliedAtDesc(driveId).stream()
                .map(a -> ApplicationResponse.of(a, drive))
                .toList();
    }

    public ApplicationResponse updateStage(UUID applicationId, Stage stage) {
        DriveApplication application = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
        application.moveTo(stage);
        DriveApplication saved = applications.save(application);
        return ApplicationResponse.of(saved, drives.findById(saved.getDriveId()).orElse(null));
    }

    // ---- helpers --------------------------------------------------------------------------

    private Drive findDrive(UUID id) {
        return drives.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Drive not found."));
    }

    private Map<UUID, Long> applicantCounts(Collection<UUID> driveIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (driveIds.isEmpty()) {
            return counts;
        }
        applications.countByDriveIds(driveIds).forEach(c -> counts.put(c.getDriveId(), c.getTotal()));
        return counts;
    }

    private Set<UUID> appliedDriveIds(UUID studentId) {
        return applications.findAllByStudentId(studentId).stream()
                .map(DriveApplication::getDriveId)
                .collect(Collectors.toSet());
    }

    private Map<UUID, Drive> drivesById(Collection<UUID> ids) {
        return drives.findAllById(ids).stream().collect(Collectors.toMap(Drive::getId, d -> d));
    }

    private static ApiException alreadyApplied() {
        return new ApiException(HttpStatus.CONFLICT, "You have already applied to this drive.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
