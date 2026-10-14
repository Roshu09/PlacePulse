package com.placepulse.notify.repository;

import com.placepulse.notify.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop30ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    boolean existsByEventIdAndRecipientId(UUID eventId, UUID recipientId);

    /** Who has already been told about this event: lets a redelivered event skip them. */
    @Query("select n.recipientId from Notification n where n.eventId = :eventId")
    List<UUID> findRecipientIdsByEventId(@Param("eventId") UUID eventId);

    @Modifying
    @Transactional
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
