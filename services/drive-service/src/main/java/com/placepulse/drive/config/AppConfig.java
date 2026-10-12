package com.placepulse.drive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** One clock for the whole service, so "today" always means the same time zone. */
    @Bean
    Clock clock(@Value("${app.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
