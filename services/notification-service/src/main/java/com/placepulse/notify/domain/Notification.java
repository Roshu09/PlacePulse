package com.placepulse.notify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * One message for one person. The pair (eventId, recipientId) is unique: Kafka delivers
 * at-least-once, so the same event can arrive twice and must not notify anyone twice.
 */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "recipient_id"}),
        indexes = @Index(columnList = "recipient_id, created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 400)
    private String body;

    /** Where in the web app this notification leads, e.g. "/applications". */
    @Column(length = 200)
    private String link;

    private Instant readAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
        // for JPA
    }

    public Notification(UUID eventId, UUID recipientId, String type, String title, String body, String link) {
        this.eventId = eventId;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getRecipientId() { return recipientId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
