package com.placepulse.drive.service;

import com.placepulse.drive.client.ProfileClient;
import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveApplication;
import com.placepulse.drive.domain.DriveLogo;
import com.placepulse.drive.domain.DriveStatus;
import com.placepulse.drive.domain.Stage;
import com.placepulse.drive.dto.DriveDtos.ApplicationResponse;
import com.placepulse.drive.dto.DriveDtos.CreateDriveRequest;
import com.placepulse.drive.dto.DriveDtos.DriveResponse;
import com.placepulse.drive.events.DriveEvents;
import com.placepulse.drive.events.EventPublisher;
import com.placepulse.drive.exception.ApiException;
import com.placepulse.drive.repository.DriveApplicationRepository;
import com.placepulse.drive.repository.DriveLogoRepository;
import com.placepulse.drive.repository.DriveRepository;
import com.placepulse.drive.security.AuthenticatedUser;
import com.placepulse.drive.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);

    private final DriveRepository drives;
    private final DriveApplicationRepository applications;
    private final DriveLogoRepository logos;
    private final ProfileClient profileClient;
    private final Clock clock;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    public DriveService(DriveRepository drives,
                        DriveApplicationRepository applications,
                        DriveLogoRepository logos,
                        ProfileClient profileClient,
                        Clock clock,
                        EventPublisher events,
                        TransactionTemplate tx) {
        this.drives = drives;
        this.applications = applications;
        this.logos = logos;
        this.profileClient = profileClient;
        this.clock = clock;
        this.events = events;
        this.tx = tx;
    }

    // ---- drives ---------------------------------------------------------------------------

    /** Logos are small icons: anything bigger is almost certainly a photo uploaded by mistake. */
    static final int LOGO_MAX_BYTES = 500 * 1024;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public DriveResponse create(AuthenticatedUser tpr, CreateDriveRequest request, MultipartFile logo) {
        LocalDate today = LocalDate.now(clock);
        byte[] logoBytes = readLogo(logo);
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
                batchSet(request),
                request.deadline(),
                tpr.id());
        // drive and logo are stored together or not at all, and the event goes out only after they are committed
        Drive saved = tx.execute(status -> {
            if (logoBytes != null) {
                drive.logoChanged(true);
            }
            Drive stored = drives.save(drive);
            if (logoBytes != null) {
                logos.save(new DriveLogo(stored.getId(), logoBytes));
            }
            return stored;
        });
        emit(saved.getId().toString(), DriveEvents.DRIVE_CREATED, new DriveEvents.DriveCreated(
                saved.getId(), saved.getCompany(), saved.getRoleTitle(), saved.getMinCgpa(),
                new ArrayList<>(saved.getEligibleBatches()), saved.getDeadline(), saved.getCreatedBy()));
        return DriveResponse.of(saved, today, 0L, null);
    }

    /**
     * Edits a drive. Applications already made stay as they are: the new rules apply to whoever applies next.
     * Students are told what changed, in plain words, and their dashboards refresh.
     */
    public DriveResponse update(UUID id, CreateDriveRequest request, MultipartFile logo) {
        LocalDate today = LocalDate.now(clock);
        byte[] logoBytes = readLogo(logo);
        record Edited(Drive drive, List<String> changes) {
        }
        Edited edited = tx.execute(status -> {
        Drive drive = findDrive(id);

        // a drive whose last date already passed can still be edited, but the date can't be moved into the past
        if (request.deadline().isBefore(today) && !request.deadline().equals(drive.getDeadline())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
                    Map.of("deadline", "The last date to apply can't be in the past."));
        }

        String company = request.company().trim();
        String roleTitle = request.roleTitle().trim();
        String description = blankToNull(request.description());
        String location = blankToNull(request.location());
        Set<Integer> batches = batchSet(request);

        List<String> changes = new ArrayList<>();
        if (!company.equals(drive.getCompany())) changes.add("company");
        if (!roleTitle.equals(drive.getRoleTitle())) changes.add("role");
        if (!java.util.Objects.equals(description, drive.getDescription())) changes.add("details");
        if (!java.util.Objects.equals(location, drive.getLocation())) changes.add("location");
        if (!sameNumber(request.packageLpa(), drive.getPackageLpa())) changes.add("package");
        if (request.minCgpa().compareTo(drive.getMinCgpa()) != 0) changes.add("minimum CGPA");
        if (!batches.equals(drive.getEligibleBatches())) changes.add("eligible batches");
        if (!request.deadline().equals(drive.getDeadline())) changes.add("last date to apply");

        drive.update(company, roleTitle, description, location, request.packageLpa(), request.minCgpa(), batches, request.deadline());

        if (logoBytes != null) {
            DriveLogo existing = logos.findById(id).orElse(null);
            if (existing == null) {
                logos.save(new DriveLogo(id, logoBytes));
            } else {
                existing.replaceWith(logoBytes);
                logos.save(existing);
            }
            drive.logoChanged(true);
            changes.add("logo");
        } else if (Boolean.TRUE.equals(request.removeLogo()) && drive.getLogoUpdatedAt() != null) {
            logos.deleteById(id);
            drive.logoChanged(false);
            changes.add("logo");
        }

        return new Edited(drives.save(drive), changes);
        });
        Drive saved = edited.drive();
        List<String> changes = edited.changes();
        // after the commit, so a dashboard that reloads because of this event sees the new data
        if (!changes.isEmpty()) {
            emit(id.toString(), DriveEvents.DRIVE_UPDATED, new DriveEvents.DriveUpdated(
                    saved.getId(), saved.getCompany(), saved.getRoleTitle(), List.copyOf(changes), saved.getCreatedBy()));
        }
        Long count = applicantCounts(List.of(id)).getOrDefault(id, 0L);
        return DriveResponse.of(saved, today, count, null);
    }

    /** The logo bytes, or 404 when the drive has none. */
    public byte[] logo(UUID id) {
        return logos.findById(id).map(DriveLogo::getData)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This drive has no logo."));
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
        emit(application.getId().toString(), DriveEvents.APPLICATION_SUBMITTED, new DriveEvents.ApplicationSubmitted(
                application.getId(), drive.getId(), drive.getCompany(), drive.getRoleTitle(),
                application.getStudentId(), application.getStudentName(), drive.getCreatedBy()));
        return ApplicationResponse.of(application, drive);
    }

    /**
     * Deletes one student's application, so they can apply again if the placement team wants them to
     * (a wrong profile at the time, a mistake). The student is told.
     */
    public void removeApplication(UUID applicationId) {
        record Removed(DriveApplication application, Drive drive) {
        }
        Removed removed = tx.execute(status -> {
            DriveApplication application = applications.findById(applicationId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
            Drive drive = drives.findById(application.getDriveId()).orElse(null);
            applications.delete(application);
            return new Removed(application, drive);
        });
        DriveApplication application = removed.application();
        Drive drive = removed.drive();
        if (drive != null) {
            emit(application.getId().toString(), DriveEvents.APPLICATION_REMOVED, new DriveEvents.ApplicationRemoved(
                    application.getId(), drive.getId(), drive.getCompany(), drive.getRoleTitle(),
                    application.getStudentId(), application.getStudentName()));
        }
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
        Stage from = application.getStage();
        application.moveTo(stage);
        DriveApplication saved = applications.save(application);
        Drive drive = drives.findById(saved.getDriveId()).orElse(null);
        if (from != stage && drive != null) {
            emit(saved.getId().toString(), DriveEvents.STAGE_CHANGED, new DriveEvents.StageChanged(
                    saved.getId(), drive.getId(), drive.getCompany(), drive.getRoleTitle(),
                    saved.getStudentId(), saved.getStudentName(), from.name(), stage.name()));
        }
        return ApplicationResponse.of(saved, drive);
    }

    // ---- helpers --------------------------------------------------------------------------

    /**
     * Called only after the database write has committed. A broker problem must never undo or fail
     * a user's action, so any failure here is logged and swallowed.
     */
    private void emit(String key, String type, Object payload) {
        try {
            events.publish(key, type, payload);
        } catch (RuntimeException e) {
            log.warn("Could not publish {}: {}", type, e.getMessage());
        }
    }

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

    private static Set<Integer> batchSet(CreateDriveRequest request) {
        return request.eligibleBatches() == null ? new TreeSet<>() : new TreeSet<>(request.eligibleBatches());
    }

    private static boolean sameNumber(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    /** Null when no file was sent. Rejects anything that isn't a small PNG. */
    private static byte[] readLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > LOGO_MAX_BYTES) {
            throw logoProblem("The logo is too large. Use a PNG under 500 KB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw logoProblem("The logo could not be read. Try again.");
        }
        if (bytes.length < PNG_MAGIC.length || !java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, PNG_MAGIC.length), PNG_MAGIC)) {
            // checks the file itself, not its name or the type the browser claims
            throw logoProblem("The logo must be a PNG image.");
        }
        return bytes;
    }

    private static ApiException logoProblem(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("logo", message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
