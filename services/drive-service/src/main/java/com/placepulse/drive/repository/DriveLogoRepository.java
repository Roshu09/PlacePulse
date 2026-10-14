package com.placepulse.drive.repository;

import com.placepulse.drive.domain.DriveLogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriveLogoRepository extends JpaRepository<DriveLogo, UUID> {
}
