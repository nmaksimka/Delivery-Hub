package com.deliveryhub.restaurant.mealdb;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(MealDbProperties.class)
public class MealDbConfig {

    public static final String NAME = "mealdb";

    @Bean
    public MealDbHttp mealDbHttp(RestClient.Builder builder, MealDbProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new MealDbHttp(builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build());
    }

    @Bean
    public MealDbClient mealDbClient(MealDbHttp http, JsonMapper jsonMapper, MealDbProperties properties,
                                     MeterRegistry meterRegistry) {
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(RestClientException.class)
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meterRegistry);
        return new MealDbClient(http, jsonMapper, rateLimiter(properties), breakers.circuitBreaker(NAME),
                retry(properties));
    }

    static RateLimiter rateLimiter(MealDbProperties properties) {
        return RateLimiter.of(NAME, RateLimiterConfig.custom()
                .limitForPeriod(properties.requestsPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(properties.rateLimitWait())
                .build());
    }

    static Retry retry(MealDbProperties properties) {
        return Retry.of(NAME, RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.retryBackoff(), 2.0))
                .retryExceptions(RestClientException.class)
                .build());
    }

    static CircuitBreaker circuitBreaker() {
        return CircuitBreaker.ofDefaults(NAME);
    }
}
