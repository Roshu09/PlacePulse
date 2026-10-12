package com.placepulse.drive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drives")
public class Drive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String company;

    @Column(nullable = false, length = 120)
    private String roleTitle;

    @Column(length = 2000)
    private String description;

    @Column(length = 120)
    private String location;

    /** Package in lakhs per annum. Optional: not every company shares it up front. */
    @Column(precision = 6, scale = 2)
    private BigDecimal packageLpa;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal minCgpa;

    /** e.g. "2025-27". Null means every batch can apply. */
    @Column(length = 20)
    private String eligibleBatch;

    /** Last day on which students can apply (inclusive). */
    @Column(nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriveStatus status = DriveStatus.OPEN;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Drive() {
        // for JPA
    }

    public Drive(String company, String roleTitle, String description, String location,
                 BigDecimal packageLpa, BigDecimal minCgpa, String eligibleBatch,
                 LocalDate deadline, UUID createdBy) {
        this.company = company;
        this.roleTitle = roleTitle;
        this.description = description;
        this.location = location;
        this.packageLpa = packageLpa;
        this.minCgpa = minCgpa;
        this.eligibleBatch = eligibleBatch;
        this.deadline = deadline;
        this.createdBy = createdBy;
    }

    /** A drive takes applications while it is open and today is not past the deadline. */
    public boolean isAccepting(LocalDate today) {
        return status == DriveStatus.OPEN && !deadline.isBefore(today);
    }

    public void setStatus(DriveStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getCompany() { return company; }
    public String getRoleTitle() { return roleTitle; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public BigDecimal getPackageLpa() { return packageLpa; }
    public BigDecimal getMinCgpa() { return minCgpa; }
    public String getEligibleBatch() { return eligibleBatch; }
    public LocalDate getDeadline() { return deadline; }
    public DriveStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
