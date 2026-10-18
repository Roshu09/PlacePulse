package com.placepulse.drive.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache. If Redis is down, slow or misbehaving, every method quietly falls back to
 * "miss" so the dashboard still works, just slower.
 */
@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisAnalyticsCache implements AnalyticsCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAnalyticsCache.class);
    static final String KEY = "placepulse:drive:analytics:v1";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisAnalyticsCache(StringRedisTemplate redis,
                               @Value("${app.cache.analytics-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<String> get() {
        try {
            return Optional.ofNullable(redis.opsForValue().get(KEY));
        } catch (RuntimeException e) {
            log.warn("Redis read failed, computing from the database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String json) {
        try {
            // the TTL is a safety net; writes also call invalidate() so data is normally fresh immediately
            redis.opsForValue().set(KEY, json, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed: {}", e.getMessage());
        }
    }

    @Override
    public void invalidate() {
        try {
            redis.delete(KEY);
        } catch (RuntimeException e) {
            log.warn("Redis invalidate failed: {}", e.getMessage());
        }
    }
}
