package com.placepulse.notify.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The Kafka entry point. If handle() throws, the error handler retries, then dead-letters the message. */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {

    private final NotificationEventHandler handler;

    public NotificationEventListener(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = "${app.events.topic}")
    public void onMessage(String json) {
        handler.handle(json);
    }
}
