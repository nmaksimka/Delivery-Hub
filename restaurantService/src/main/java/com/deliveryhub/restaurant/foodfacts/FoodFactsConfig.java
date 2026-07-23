package com.deliveryhub.restaurant.foodfacts;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FoodFactsProperties.class)
public class FoodFactsConfig {

    public static final String NAME = "foodfacts";

    @Bean
    public FoodFactsHttp foodFactsHttp(RestClient.Builder builder, FoodFactsProperties properties,
                                       MeterRegistry meterRegistry) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(circuitBreakerConfig());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meterRegistry);
        RestClient restClient = builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestFactory(requestFactory)
                .build();
        return new FoodFactsHttp(restClient, rateLimiter(properties), breakers.circuitBreaker(NAME),
                retry(properties));
    }

    @Bean
    public FoodFactsClient foodFactsClient(FoodFactsHttp http, JsonMapper jsonMapper) {
        return new FoodFactsClient(http, jsonMapper);
    }


    static RateLimiter rateLimiter(FoodFactsProperties properties) {
        return RateLimiter.of(NAME, RateLimiterConfig.custom()
                .limitForPeriod(properties.requestsPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(properties.rateLimitWait())
                .build());
    }

    static Retry retry(FoodFactsProperties properties) {
        return Retry.of(NAME, RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.retryBackoff(), 2.0))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    }

    private static CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build();
    }
}
