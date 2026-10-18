package com.placepulse.notify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A placement representative this service has seen, so alerts can go to the whole team, not just one person. */
@Entity
@Table(name = "known_tprs")
public class KnownTpr {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant firstSeenAt = Instant.now();

    protected KnownTpr() {
        // for JPA
    }

    public KnownTpr(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
}
