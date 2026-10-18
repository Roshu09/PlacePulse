package com.placepulse.auth.events;

/** Announces that something happened, so other services can react without auth-service knowing who they are. */
public interface EventPublisher {

    /**
     * @param key     events with the same key keep their order (we use the student id)
     * @param type    e.g. "profile.edited"
     * @param payload the facts of the event, serialised to JSON
     */
    void publish(String key, String type, Object payload);
}
