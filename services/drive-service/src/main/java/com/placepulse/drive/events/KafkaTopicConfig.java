package com.placepulse.drive.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the topic on startup if it is missing (a no-op when it exists). */
@Configuration
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    NewTopic eventsTopic(@Value("${app.events.topic}") String topic) {
        // 3 partitions: events with the same key (one application) stay ordered, different ones can be consumed in parallel
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
