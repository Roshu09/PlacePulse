package com.placepulse.auth.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used in tests and when EVENTS_ENABLED=false: events are simply dropped. */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "false")
public class NoOpEventPublisher implements EventPublisher {

    @Override
    public void publish(String key, String type, Object payload) {
        // intentionally empty
    }
}
