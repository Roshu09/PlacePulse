package com.placepulse.drive.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka,
                               ObjectMapper mapper,
                               @Value("${app.events.topic}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void publish(String key, String type, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(new DriveEvents.Envelope(UUID.randomUUID(), type, Instant.now(), payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event " + type, e);
        }
        kafka.send(topic, key, json).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Event {} (key {}) was not delivered: {}", type, key, error.getMessage());
            } else {
                log.debug("Event {} sent to {}-{} @ {}", type, result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
