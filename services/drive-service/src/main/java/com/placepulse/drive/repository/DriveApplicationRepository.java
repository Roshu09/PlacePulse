package com.placepulse.drive.repository;

import com.placepulse.drive.domain.DriveApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placepulse.drive.domain.Stage;
import java.time.Instant;
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

    // ---- analytics: all aggregation happens in the database ------------------------------------

    interface DriveStageCount {
        UUID getDriveId();

        Stage getStage();

        long getTotal();
    }

    @Query("select a.driveId as driveId, a.stage as stage, count(a) as total from DriveApplication a "
            + "group by a.driveId, a.stage")
    List<DriveStageCount> countByDriveAndStage();

    @Query("select count(distinct a.studentId) from DriveApplication a")
    long countDistinctStudents();

    @Query("select count(distinct a.studentId) from DriveApplication a where a.stage = :stage")
    long countDistinctStudentsAtStage(@Param("stage") Stage stage);

    @Query("select avg(a.cgpa) from DriveApplication a")
    Double averageCgpa();

    @Query("select avg(a.cgpa) from DriveApplication a where a.stage = :stage")
    Double averageCgpaAtStage(@Param("stage") Stage stage);

    /** Only the timestamp column, for the last couple of weeks; days are bucketed in the app's own time zone. */
    @Query("select a.appliedAt from DriveApplication a where a.appliedAt >= :since")
    List<Instant> appliedAtSince(@Param("since") Instant since);
}
