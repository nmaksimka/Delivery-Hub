package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoClientsTest {

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());
    private static final GeoPosition FROM = new GeoPosition(56.4846, 84.9476);
    private static final GeoPosition TO = new GeoPosition(56.47, 84.97);

    private final StraightLine straightLine = mock(StraightLine.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-18T05:00:00Z"));
    private RoutePlanner routePlanner;
    private WeatherReport weatherReport;

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void setUp() {
        SERVER.resetAll();
        RestClient restClient = RestClient.builder().baseUrl(SERVER.baseUrl()).build();
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        routePlanner = new RoutePlanner(restClient, CircuitBreaker.ofDefaults("osrm"), RateLimiter.ofDefaults("osrm"),
                straightLine, new RoutingProperties(SERVER.baseUrl(), "car", "test", Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1.4, 25), clock);
        weatherReport = new WeatherReport(restClient, CircuitBreaker.ofDefaults("weather"),
                new WeatherProperties(SERVER.baseUrl(), 56.4977, 84.9744, Duration.ofSeconds(1), Duration.ofSeconds(1),
                        Duration.ofMinutes(10), -25), clock);
    }

    @Test
    void takesDistanceAndDurationFromOsrm() {
        SERVER.stubFor(get(urlPathMatching("/route/v1/car/84\\.947600,56\\.484600;84\\.970000,56\\.470000"))
                .willReturn(okJson("{\"code\": \"Ok\", \"routes\": [{\"distance\": 2793.4, \"duration\": 260.9}]}")));

        Route route = routePlanner.route(FROM, TO);

        assertThat(route).isEqualTo(new Route(2793, 261, Route.RouteSource.OSRM));
    }

    @Test
    void cachesOsrmRouteForTenMinutes() {
        SERVER.stubFor(get(urlPathMatching("/route/.*"))
                .willReturn(okJson("{\"code\": \"Ok\", \"routes\": [{\"distance\": 2793.4, \"duration\": 260.9}]}")));

        routePlanner.route(FROM, TO);
        routePlanner.route(FROM, TO);
        SERVER.verify(1, getRequestedFor(urlPathMatching("/route/.*")));

        now.set(now.get().plus(Duration.ofMinutes(11)));
        routePlanner.route(FROM, TO);
        SERVER.verify(2, getRequestedFor(urlPathMatching("/route/.*")));
    }

    @Test
    void rateLimitedOsrmFallsBackToStraightLine() {
        RoutePlanner limited = new RoutePlanner(RestClient.builder().baseUrl(SERVER.baseUrl()).build(),
                CircuitBreaker.ofDefaults("osrm"), RateLimiter.of("osrm", RateLimiterConfig.custom()
                .limitForPeriod(1).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build()),
                straightLine, new RoutingProperties(SERVER.baseUrl(), "car", "test", Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1.4, 25), Clock.systemUTC());
        SERVER.stubFor(get(urlPathMatching("/route/.*"))
                .willReturn(okJson("{\"code\": \"Ok\", \"routes\": [{\"distance\": 2793.4, \"duration\": 260.9}]}")));
        when(straightLine.meters(TO, FROM)).thenReturn(1000.0);

        limited.route(FROM, TO);

        assertThat(limited.route(TO, FROM).source()).isEqualTo(Route.RouteSource.STRAIGHT_LINE);
    }

    @Test
    void unreadableGeometryFallsBackToStraightPath() {
        SERVER.stubFor(get(urlPathMatching("/route/.*")).willReturn(okJson(
                "{\"code\": \"Ok\", \"routes\": [{\"distance\": 10, \"duration\": 5,"
                        + " \"geometry\": {\"coordinates\": [[84.9], [84.97]]}}]}")));
        when(straightLine.meters(FROM, TO)).thenReturn(1000.0);

        assertThat(routePlanner.path(FROM, TO).source()).isEqualTo(Route.RouteSource.STRAIGHT_LINE);
    }

    @Test
    void fallsBackToStraightLineWhenOsrmFails() {
        SERVER.stubFor(get(urlPathMatching("/route/.*")).willReturn(aResponse().withStatus(502)));
        when(straightLine.meters(FROM, TO)).thenReturn(2000.0);

        Route route = routePlanner.route(FROM, TO);

        assertThat(route.source()).isEqualTo(Route.RouteSource.STRAIGHT_LINE);
        assertThat(route.distanceMeters()).isEqualTo(2800);
        assertThat(route.durationSeconds()).isEqualTo(403);
    }

    @Test
    void fallsBackWhenOsrmFindsNoRoute() {
        SERVER.stubFor(get(urlPathMatching("/route/.*")).willReturn(okJson("{\"code\": \"NoRoute\", \"routes\": []}")));
        when(straightLine.meters(FROM, TO)).thenReturn(1000.0);

        assertThat(routePlanner.route(FROM, TO).source()).isEqualTo(Route.RouteSource.STRAIGHT_LINE);
    }

    @Test
    void recognisesSnowAndCachesWeatherForTenMinutes() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(okJson(current(-8.0, 0, 0.7, 73))));

        Weather first = weatherReport.current();
        now.set(now.get().plus(Duration.ofMinutes(9)));
        weatherReport.current();

        assertThat(first).isEqualTo(new Weather(WeatherCondition.SNOW, -8.0));
        SERVER.verify(1, getRequestedFor(urlPathEqualTo("/v1/forecast")));
        now.set(now.get().plus(Duration.ofMinutes(2)));
        weatherReport.current();
        SERVER.verify(2, getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }

    @Test
    void severeFrostWinsOverClearSky() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(okJson(current(-31.5, 0, 0, 0))));

        assertThat(weatherReport.current().condition()).isEqualTo(WeatherCondition.FROST);
    }

    @Test
    void rainIsRecognisedByPrecipitation() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(okJson(current(12.0, 1.2, 0, 3))));

        assertThat(weatherReport.current().condition()).isEqualTo(WeatherCondition.RAIN);
    }

    @Test
    void unavailableWeatherIsUnknown() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(503)));

        assertThat(weatherReport.current()).isEqualTo(Weather.unknown());
    }

    @Test
    void failedLookupIsNotRepeatedForHalfAMinute() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(503)));

        weatherReport.current();
        int afterFirstLookup = SERVER.findAll(getRequestedFor(urlPathEqualTo("/v1/forecast"))).size();
        now.set(now.get().plus(Duration.ofSeconds(30)));
        weatherReport.current();

        assertThat(SERVER.findAll(getRequestedFor(urlPathEqualTo("/v1/forecast")))).hasSize(afterFirstLookup);
    }

    @Test
    void keepsLastKnownWeatherWhenOpenMeteoFails() {
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(okJson(current(-8.0, 0, 0.7, 73))));
        weatherReport.current();
        SERVER.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(503)));
        now.set(now.get().plus(Duration.ofMinutes(11)));

        assertThat(weatherReport.current()).isEqualTo(new Weather(WeatherCondition.SNOW, -8.0));
    }

    private static String current(double temperature, double rain, double snowfall, int code) {
        return "{\"current\": {\"temperature_2m\": " + temperature + ", \"rain\": " + rain + ", \"showers\": 0.0,"
                + " \"snowfall\": " + snowfall + ", \"weather_code\": " + code + "}}";
    }
}
