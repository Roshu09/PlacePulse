package com.placepulse.drive.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Creates the topic on startup if it is missing (a no-op when it exists). */
@Configuration
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    NewTopic eventsTopic(@Value("${app.events.topic}") String topic) {
        // 3 partitions: events with the same key (one application) stay ordered, different ones can be consumed in parallel
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /** Messages this service cannot process are parked here instead of blocking everything behind them. */
    @Bean
    NewTopic deadLetterTopic(@Value("${app.events.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }

    /** A failing message is retried 3 times, one second apart, then moved to the dead-letter topic. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3L));
    }
}
