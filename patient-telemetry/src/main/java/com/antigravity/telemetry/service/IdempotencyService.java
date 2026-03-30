package com.antigravity.telemetry.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    // 24 hour TTL to prevent unbounded cache growth but provide enough window for idempotency
    private static final Duration TTL = Duration.ofHours(24);

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tries to establish idempotency using Redis SETNX (set if absent).
     * @return true if the event has NOT been processed before
     */
    public boolean isNewEvent(String eventId) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent("telemetry:event:" + eventId, "processed", TTL);
        return Boolean.TRUE.equals(success);
    }
}
