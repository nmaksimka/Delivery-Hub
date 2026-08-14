package com.deliveryhub.delivery.quote;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

public class WeatherReport {

    private static final Logger log = LoggerFactory.getLogger(WeatherReport.class);
    private static final Set<Integer> SNOW_CODES = Set.of(71, 73, 75, 77, 85, 86);
    private static final Set<Integer> RAIN_CODES = Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final WeatherProperties properties;
    private final Clock clock;
    private Weather cached;
    private Instant cachedUntil = Instant.MIN;

    public WeatherReport(RestClient restClient, CircuitBreaker circuitBreaker, WeatherProperties properties,
                         Clock clock) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Weather current() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cachedUntil)) {
            return cached;
        }
        try {
            OpenMeteoResponse response = circuitBreaker.executeSupplier(this::fetch);
            if (response == null || response.current() == null) {
                return Weather.unknown();
            }
            cached = interpret(response.current());
            cachedUntil = now.plus(properties.cacheTtl());
            return cached;
        } catch (RestClientException | CallNotPermittedException e) {
            log.warn("Open-Meteo is unavailable, quoting without weather: {}", e.getMessage());
            return Weather.unknown();
        }
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

    record OpenMeteoResponse(Current current) {
    }

    record Current(@JsonProperty("temperature_2m") double temperature, double rain, double showers, double snowfall,
                   @JsonProperty("weather_code") int weatherCode) {
    }
}
