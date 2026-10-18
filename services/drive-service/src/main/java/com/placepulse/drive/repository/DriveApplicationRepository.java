package com.placepulse.drive.repository;

import com.placepulse.drive.domain.DriveApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DriveApplicationRepository extends JpaRepository<DriveApplication, UUID> {

    interface DriveCount {
        UUID getDriveId();

        long getTotal();
    }

    boolean existsByDriveIdAndStudentId(UUID driveId, UUID studentId);

    List<DriveApplication> findAllByStudentId(UUID studentId);

    List<DriveApplication> findAllByStudentIdAndStageIn(UUID studentId, Collection<com.placepulse.drive.domain.Stage> stages);

    List<DriveApplication> findAllByStudentIdOrderByAppliedAtDesc(UUID studentId);

    List<DriveApplication> findAllByDriveIdOrderByAppliedAtDesc(UUID driveId);

    @Query("select a.driveId as driveId, count(a) as total from DriveApplication a "
            + "where a.driveId in :driveIds group by a.driveId")
    List<DriveCount> countByDriveIds(@Param("driveIds") Collection<UUID> driveIds);
}
