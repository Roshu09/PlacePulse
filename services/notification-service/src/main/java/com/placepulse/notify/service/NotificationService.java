package com.placepulse.notify.service;

import com.placepulse.notify.domain.Notification;
import com.placepulse.notify.dto.NotificationDtos.NotificationResponse;
import com.placepulse.notify.dto.NotificationDtos.NotificationsResponse;
import com.placepulse.notify.exception.ApiException;
import com.placepulse.notify.repository.NotificationRepository;
import com.placepulse.notify.security.AuthenticatedUser;
import com.placepulse.notify.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final StudentDirectory directory;
    private final SseHub hub;

    public NotificationService(NotificationRepository notifications, StudentDirectory directory, SseHub hub) {
        this.notifications = notifications;
        this.directory = directory;
        this.hub = hub;
    }

    public SseEmitter stream(AuthenticatedUser user) {
        if (user.role() == Role.STUDENT) {
            // a student with the app open is reachable for "new drive" even before the first list call
            directory.remember(user.id());
        }
        return hub.subscribe(user.id());
    }

    public NotificationsResponse list(AuthenticatedUser user) {
        if (user.role() == Role.STUDENT) {
            // the bell loads on every page, so this is how a student first becomes reachable for "new drive"
            directory.remember(user.id());
        }
        var items = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(user.id()).stream()
                .map(NotificationResponse::of)
                .toList();
        return new NotificationsResponse(notifications.countByRecipientIdAndReadAtIsNull(user.id()), items);
    }

    public NotificationResponse markRead(UUID id, AuthenticatedUser user) {
        // someone else's notification looks exactly like one that doesn't exist
        Notification n = notifications.findByIdAndRecipientId(id, user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
        n.markRead();
        return NotificationResponse.of(notifications.save(n));
    }

    public int markAllRead(AuthenticatedUser user) {
        return notifications.markAllRead(user.id(), Instant.now());
    }
}
