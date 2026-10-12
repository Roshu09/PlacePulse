package com.placepulse.drive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One student's application to one drive.
 *
 * The student's name, email, CGPA, course and batch are copied in when they apply. This service
 * does not own user data (auth-service does), and the snapshot also records what the student's
 * CGPA was at the time they applied.
 */
@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"drive_id", "student_id"}))
public class DriveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "drive_id", nullable = false)
    private UUID driveId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 100)
    private String studentName;

    @Column(nullable = false)
    private String studentEmail;

    @Column(precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(length = 60)
    private String course;

    @Column(length = 20)
    private String batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Stage stage = Stage.APPLIED;

    @Column(nullable = false, updatable = false)
    private Instant appliedAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DriveApplication() {
        // for JPA
    }

    public DriveApplication(UUID driveId, UUID studentId, String studentName, String studentEmail,
                            BigDecimal cgpa, String course, String batch) {
        this.driveId = driveId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.cgpa = cgpa;
        this.course = course;
        this.batch = batch;
    }

    public void moveTo(Stage stage) {
        this.stage = stage;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDriveId() { return driveId; }
    public UUID getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentEmail() { return studentEmail; }
    public BigDecimal getCgpa() { return cgpa; }
    public String getCourse() { return course; }
    public String getBatch() { return batch; }
    public Stage getStage() { return stage; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
