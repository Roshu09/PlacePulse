package com.placepulse.notify.dto;

import com.placepulse.notify.domain.Notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(UUID id, String type, String title, String body, String link,
                                       boolean read, Instant createdAt) {

        public static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    /** The latest notifications plus the unread total, so the bell needs only one request. */
    public record NotificationsResponse(long unreadCount, List<NotificationResponse> items) {
    }

    public record ReadAllResponse(int updated) {
    }
}
