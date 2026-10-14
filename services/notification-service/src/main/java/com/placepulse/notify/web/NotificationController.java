package com.placepulse.notify.web;

import com.placepulse.notify.dto.NotificationDtos.NotificationResponse;
import com.placepulse.notify.dto.NotificationDtos.NotificationsResponse;
import com.placepulse.notify.dto.NotificationDtos.ReadAllResponse;
import com.placepulse.notify.security.AuthenticatedUser;
import com.placepulse.notify.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationsResponse list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user);
    }

    /** Live updates. The browser opens this once and new notifications arrive on it as they happen. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.stream(user);
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.markRead(id, user);
    }

    @PostMapping("/read-all")
    public ReadAllResponse markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return new ReadAllResponse(service.markAllRead(user));
    }
}
