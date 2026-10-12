package com.placepulse.drive.repository;

import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.domain.DriveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DriveRepository extends JpaRepository<Drive, UUID> {

    /** Drives a student can still apply to: open, and the last date has not passed. */
    List<Drive> findByStatusAndDeadlineGreaterThanEqualOrderByDeadlineAsc(DriveStatus status, LocalDate today);

    List<Drive> findAllByOrderByCreatedAtDesc();
}
