package com.placepulse.drive.events;

/** Announces that something happened, so other services can react without drive-service knowing who they are. */
public interface EventPublisher {

    /**
     * @param key   events with the same key keep their order (we use the application or drive id)
     * @param type  e.g. "application.submitted"
     * @param payload the facts of the event, serialised to JSON
     */
    void publish(String key, String type, Object payload);
}
