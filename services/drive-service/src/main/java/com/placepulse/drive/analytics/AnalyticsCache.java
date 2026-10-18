package com.placepulse.drive.analytics;

import java.util.Optional;

/** Holds the finished analytics JSON. Any failure must behave like "not cached", never like an error. */
public interface AnalyticsCache {

    Optional<String> get();

    void put(String json);

    void invalidate();
}
