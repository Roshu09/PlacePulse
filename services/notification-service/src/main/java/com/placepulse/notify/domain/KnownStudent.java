package com.placepulse.notify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A student this service has seen. "New drive" has no single recipient, so we fan it out to everyone
 * in this list. A student joins it the first time they open the app (or apply to a drive).
 */
@Entity
@Table(name = "known_students")
public class KnownStudent {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant firstSeenAt = Instant.now();

    protected KnownStudent() {
        // for JPA
    }

    public KnownStudent(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
}
