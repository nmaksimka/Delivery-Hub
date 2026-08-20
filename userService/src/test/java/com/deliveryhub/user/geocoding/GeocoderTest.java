package com.deliveryhub.user.geocoding;

import com.deliveryhub.user.entity.GeoPoint;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeocoderTest {

    private static final WireMockServer NOMINATIM = new WireMockServer(options().dynamicPort());
    private static final String USER_AGENT = "DeliveryHub/1.0 (portfolio demo)";

    private Geocoder geocoder;

    @BeforeAll
    static void start() {
        NOMINATIM.start();
    }

    @AfterAll
    static void stop() {
        NOMINATIM.stop();
    }

    @BeforeEach
    void setUp() {
        NOMINATIM.resetAll();
        NominatimProperties properties = new NominatimProperties(NOMINATIM.baseUrl(), USER_AGENT, "Томск", "ru",
                "84.80,56.62,85.20,56.36", Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(1),
                100, Duration.ofDays(1), Duration.ofMinutes(10), 20);
        RestClient restClient = RestClient.builder().baseUrl(NOMINATIM.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        RateLimiter unlimited = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(1000).limitRefreshPeriod(Duration.ofSeconds(1)).build());
        geocoder = new Geocoder(restClient, unlimited, CircuitBreaker.ofDefaults("test"), properties,
                Clock.fixed(Instant.parse("2026-07-14T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void asksForHouseInsideTomskAndReadsCoordinates() {
        NOMINATIM.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(okJson("[{\"lat\": \"56.4846453\", \"lon\": \"84.9476493\", \"display_name\": \"Ленина 12\"}]")));

        Optional<GeoPoint> point = geocoder.locate("проспект Ленина", "12");

        assertThat(point).hasValueSatisfying(found -> {
            assertThat(found.latitude()).isEqualByComparingTo("56.484645");
            assertThat(found.longitude()).isEqualByComparingTo("84.947649");
        });
        NOMINATIM.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(USER_AGENT))
                .withQueryParam("street", equalTo("12 проспект Ленина"))
                .withQueryParam("city", equalTo("Томск"))
                .withQueryParam("countrycodes", equalTo("ru"))
                .withQueryParam("bounded", equalTo("1"))
                .withQueryParam("viewbox", equalTo("84.80,56.62,85.20,56.36")));
    }

    @Test
    void emptyAnswerMeansAddressIsNotOnMap() {
        NOMINATIM.stubFor(get(urlPathEqualTo("/search")).willReturn(okJson("[]")));

        assertThat(geocoder.locate("Несуществующая", "999")).isEmpty();
    }

    @Test
    void repeatedLookupIsServedFromCache() {
        NOMINATIM.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(okJson("[{\"lat\": \"56.47\", \"lon\": \"84.95\"}]")));

        geocoder.locate("Фрунзе", "20");
        geocoder.locate(" фрунзе ", "20");

        NOMINATIM.verify(1, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void serverErrorMeansGeocoderIsUnavailable() {
        NOMINATIM.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> geocoder.locate("Ленина", "1")).isInstanceOf(GeocoderUnavailableException.class);
    }

    @Test
    void unreadableCoordinatesMeanAddressIsNotOnMap() {
        NOMINATIM.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(okJson("[{\"lat\": \"999\", \"lon\": \"84.95\"}]")));

        assertThat(geocoder.locate("Кривая", "1")).isEmpty();
    }
}
