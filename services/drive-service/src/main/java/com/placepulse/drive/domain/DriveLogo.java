package com.placepulse.drive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The company logo (a small PNG). Kept apart from Drive so listing drives never loads image bytes. */
@Entity
@Table(name = "drive_logos")
public class DriveLogo {

    @Id
    private UUID driveId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 600_000)
    private byte[] data;

    protected DriveLogo() {
        // for JPA
    }

    public DriveLogo(UUID driveId, byte[] data) {
        this.driveId = driveId;
        this.data = data;
    }

    public void replaceWith(byte[] data) {
        this.data = data;
    }

    public UUID getDriveId() { return driveId; }
    public byte[] getData() { return data; }
}
