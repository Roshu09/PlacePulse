package com.placepulse.notify.service;

import com.placepulse.notify.dto.NotificationDtos.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps one open connection per browser tab and pushes a notification down it the moment it is saved,
 * so dashboards no longer wait for a poll. Connections live in memory: if this service restarts, browsers
 * simply reconnect (and re-fetch what they missed).
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    /** Shorter than the 60 minute login token; the browser reconnects with its current token. */
    static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> connections = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        // add and remove are atomic per user, so a connection can never be added to a list that is being discarded
        connections.compute(userId, (id, list) -> {
            List<SseEmitter> mine = list == null ? new CopyOnWriteArrayList<>() : list;
            mine.add(emitter);
            return mine;
        });

        Runnable remove = () -> connections.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
        emitter.onCompletion(remove);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> remove.run());

        // tells the browser the stream is open, and flushes the response headers right away
        send(userId, emitter, SseEmitter.event().name("ready").data("ok"));
        return emitter;
    }

    /** Never throws: a broken connection must not stop the notification from being stored or other users from getting theirs. */
    public void push(UUID userId, NotificationResponse notification) {
        List<SseEmitter> mine = connections.get(userId);
        if (mine == null) {
            return;
        }
        for (SseEmitter emitter : mine) {
            send(userId, emitter, SseEmitter.event().name("notification").id(notification.id().toString()).data(notification));
        }
    }

    /** Keeps idle connections alive through proxies, and lets the browser notice a dead one. */
    @Scheduled(fixedDelay = 20_000)
    void heartbeat() {
        connections.forEach((userId, list) -> list.forEach(e -> send(userId, e, SseEmitter.event().name("ping").data("1"))));
    }

    public int connectionCount() {
        return connections.values().stream().mapToInt(List::size).sum();
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // the tab was closed or the connection already finished
            log.debug("Dropping a dead stream for {}: {}", userId, e.getMessage());
            emitter.completeWithError(e);
        }
    }
}
