package com.deliveryhub.restaurant.foodfacts;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

public class FoodFactsHttp {

    public static final String CACHE = "foodfacts";

    private final RestClient restClient;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public FoodFactsHttp(RestClient restClient, RateLimiter rateLimiter, CircuitBreaker circuitBreaker, Retry retry) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Cacheable(cacheNames = CACHE, unless = "#result == null")
    public String get(String pathAndQuery) {
        Supplier<String> call = () -> restClient.get().uri(pathAndQuery).retrieve().body(String.class);
        Supplier<String> limited = RateLimiter.decorateSupplier(rateLimiter, call);
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, limited)).get();
    }
}
