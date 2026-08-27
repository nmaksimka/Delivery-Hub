package com.deliveryhub.gateway.ticket;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class WebSocketTickets {

    static final Duration TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "ws-ticket:";

    private final ReactiveStringRedisTemplate redis;

    public WebSocketTickets(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> issue(String accessToken) {
        String ticket = UUID.randomUUID().toString();
        return redis.opsForValue().set(KEY_PREFIX + ticket, accessToken, TTL).thenReturn(ticket);
    }

    public Mono<String> redeem(String ticket) {
        return redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
    }
}
