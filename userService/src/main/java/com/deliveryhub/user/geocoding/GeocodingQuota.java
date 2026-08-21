package com.deliveryhub.user.geocoding;

import com.deliveryhub.user.exception.GeocodingQuotaExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeocodingQuota {

    public static final int LOOKUPS_PER_HOUR = 10;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final Map<Long, Deque<Instant>> lookups = new ConcurrentHashMap<>();

    public GeocodingQuota(Clock clock) {
        this.clock = clock;
    }

    public void consume(Long userId) {
        Instant now = clock.instant();
        lookups.compute(userId, (id, recent) -> {
            Deque<Instant> window = recent == null ? new ArrayDeque<>() : recent;
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= LOOKUPS_PER_HOUR) {
                throw new GeocodingQuotaExceededException(LOOKUPS_PER_HOUR);
            }
            window.addLast(now);
            return window;
        });
    }
}
