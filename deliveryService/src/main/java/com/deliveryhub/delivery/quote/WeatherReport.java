package com.deliveryhub.delivery.quote;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class WeatherReport {

    private static final Logger log = LoggerFactory.getLogger(WeatherReport.class);
    private static final Set<Integer> SNOW_CODES = Set.of(71, 73, 75, 77, 85, 86);
    private static final Set<Integer> RAIN_CODES = Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99);

    private static final Duration FAILURE_TTL = Duration.ofSeconds(45);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final WeatherProperties properties;
    private final Clock clock;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Snapshot snapshot;

    public WeatherReport(RestClient restClient, CircuitBreaker circuitBreaker, WeatherProperties properties,
                         Clock clock) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.clock = clock;
    }

    public Weather current() {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.until())) {
            return current.weather();
        }
        if (!refreshing.compareAndSet(false, true)) {
            return current == null ? Weather.unknown() : current.weather();
        }
        try {
            Snapshot next = refresh(current, now);
            snapshot = next;
            return next.weather();
        } finally {
            refreshing.set(false);
        }
    }

    private Snapshot refresh(Snapshot stale, Instant now) {
        try {
            OpenMeteoResponse response = circuitBreaker.executeSupplier(this::fetch);
            if (response != null && response.current() != null) {
                return new Snapshot(interpret(response.current()), now.plus(properties.cacheTtl()));
            }
        } catch (RestClientException | CallNotPermittedException e) {
            log.warn("Open-Meteo is unavailable, quoting with the last known weather: {}", e.getClass().getSimpleName());
        }
        Weather fallback = stale == null ? Weather.unknown() : stale.weather();
        return new Snapshot(fallback, now.plus(FAILURE_TTL));
    }

    private OpenMeteoResponse fetch() {
        return restClient.get()
                .uri(builder -> builder.path("/v1/forecast")
                        .queryParam("latitude", properties.latitude())
                        .queryParam("longitude", properties.longitude())
                        .queryParam("current", "temperature_2m,rain,showers,snowfall,weather_code")
                        .build())
                .retrieve()
                .body(OpenMeteoResponse.class);
    }

    private Weather interpret(Current current) {
        double temperature = current.temperature();
        if (temperature < properties.frostBelowCelsius()) {
            return new Weather(WeatherCondition.FROST, temperature);
        }
        if (current.snowfall() > 0 || SNOW_CODES.contains(current.weatherCode())) {
            return new Weather(WeatherCondition.SNOW, temperature);
        }
        if (current.rain() > 0 || current.showers() > 0 || RAIN_CODES.contains(current.weatherCode())) {
            return new Weather(WeatherCondition.RAIN, temperature);
        }
        return new Weather(WeatherCondition.CLEAR, temperature);
    }

    private record Snapshot(Weather weather, Instant until) {
    }

    record OpenMeteoResponse(Current current) {
    }

    record Current(@JsonProperty("temperature_2m") double temperature, double rain, double showers, double snowfall,
                   @JsonProperty("weather_code") int weatherCode) {
    }
}
