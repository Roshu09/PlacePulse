package com.placepulse.drive.analytics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Used when app.cache.enabled=false: every request is computed from the database (this is the "before" in benchmarks). */
@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false")
public class NoOpAnalyticsCache implements AnalyticsCache {

    @Override
    public Optional<String> get() {
        return Optional.empty();
    }

    @Override
    public void put(String json) {
    }

    @Override
    public void invalidate() {
    }
}
