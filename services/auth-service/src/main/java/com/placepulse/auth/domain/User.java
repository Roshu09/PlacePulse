package com.placepulse.auth.domain;

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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Student profile, filled in after sign-up. Drive eligibility is checked against these.
    @Column(precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(length = 60)
    private String course;

    @Column(length = 20)
    private String batch;

    @Column(length = 20)
    private String phone;

    @Column(precision = 5, scale = 2)
    private BigDecimal tenthPercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal twelfthPercent;

    @Column(length = 300)
    private String resumeUrl;

    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 80)
    private String city;

    @Column(length = 60)
    private String graduationDegree;

    @Column(precision = 5, scale = 2)
    private BigDecimal graduationPercent;

    @Column(length = 120)
    private String graduationUniversity;

    private Integer activeBacklogs;

    @Column(length = 300)
    private String githubUrl;

    @Column(length = 300)
    private String linkedinUrl;

    @Column(length = 300)
    private String portfolioUrl;

    @Column(length = 500)
    private String skills;

    @Column(length = 1500)
    private String projects;

    @Column(length = 1000)
    private String certifications;

    /** When the student registered for placements; null until they do. Once set, editing the profile has consequences. */
    private Instant placementRegisteredAt;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getCgpa() { return cgpa; }
    public String getCourse() { return course; }
    public String getBatch() { return batch; }

    public boolean isPlacementRegistered() { return placementRegisteredAt != null; }
    public Instant getPlacementRegisteredAt() { return placementRegisteredAt; }

    public void registerForPlacements() {
        if (placementRegisteredAt == null) {
            placementRegisteredAt = Instant.now();
        }
    }

    /** The editable part of the profile, as stored. */
    public ProfileData profile() {
        return new ProfileData(phone, course, batch, cgpa, tenthPercent, twelfthPercent, resumeUrl,
                dateOfBirth, gender, city, graduationDegree, graduationPercent, graduationUniversity,
                activeBacklogs, githubUrl, linkedinUrl, portfolioUrl, skills, projects, certifications);
    }

    /** Replaces the whole profile: a null field clears that value. */
    public void applyProfile(ProfileData p) {
        this.phone = p.phone();
        this.course = p.course();
        this.batch = p.batch();
        this.cgpa = p.cgpa();
        this.tenthPercent = p.tenthPercent();
        this.twelfthPercent = p.twelfthPercent();
        this.resumeUrl = p.resumeUrl();
        this.dateOfBirth = p.dateOfBirth();
        this.gender = p.gender();
        this.city = p.city();
        this.graduationDegree = p.graduationDegree();
        this.graduationPercent = p.graduationPercent();
        this.graduationUniversity = p.graduationUniversity();
        this.activeBacklogs = p.activeBacklogs();
        this.githubUrl = p.githubUrl();
        this.linkedinUrl = p.linkedinUrl();
        this.portfolioUrl = p.portfolioUrl();
        this.skills = p.skills();
        this.projects = p.projects();
        this.certifications = p.certifications();
    }
}
