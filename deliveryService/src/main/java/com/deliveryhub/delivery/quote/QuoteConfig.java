package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.dispatch.DeliveryZones;
import com.deliveryhub.delivery.dispatch.DispatchProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({RoutingProperties.class, WeatherProperties.class, PricingProperties.class})
public class QuoteConfig {

    private final CircuitBreakerRegistry breakers;

    public QuoteConfig(MeterRegistry meterRegistry) {
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class,
                        HttpClientErrorException.TooManyRequests.class)
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meterRegistry);
    }

    @Bean
    public RoutePlanner routePlanner(RestClient.Builder builder, RoutingProperties properties, StraightLine straightLine,
                                     Clock clock) {
        RestClient restClient = builder.clone().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        RateLimiter rateLimiter = RateLimiter.of("osrm", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        return new RoutePlanner(restClient, breakers.circuitBreaker("osrm"), rateLimiter, straightLine, properties, clock);
    }

    @Bean
    public WeatherReport weatherReport(RestClient.Builder builder, WeatherProperties properties, Clock clock) {
        RestClient restClient = builder.clone().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .build();
        return new WeatherReport(restClient, breakers.circuitBreaker("open-meteo"), properties, clock);
    }

    @Bean
    public DeliveryQuotes deliveryQuotes(DeliveryZones zones, StraightLine straightLine, RoutePlanner routePlanner,
                                         WeatherReport weatherReport, PricingProperties pricing,
                                         DispatchProperties dispatch) {
        return new DeliveryQuotes(zones, straightLine, routePlanner, weatherReport, pricing, dispatch);
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
