package com.deliveryhub.delivery;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class DeliveryQuoteIT {

    private static final WireMockServer GEO_APIS = new WireMockServer(options().dynamicPort());
    private static final String QUOTE = "/internal/delivery/quote";

    static {
        GEO_APIS.start();
    }

    @Autowired
    private MockMvcTester mvc;

    @DynamicPropertySource
    static void geoApisAreWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.routing.base-url", GEO_APIS::baseUrl);
        registry.add("app.weather.base-url", GEO_APIS::baseUrl);
        registry.add("app.weather.cache-ttl", () -> "0s");
    }

    @AfterAll
    static void stopGeoApis() {
        GEO_APIS.stop();
    }

    @BeforeEach
    void stubGeoApis() {
        GEO_APIS.resetAll();
        GEO_APIS.stubFor(get(urlPathMatching("/route/v1/car/.*"))
                .willReturn(okJson("{\"code\": \"Ok\", \"routes\": [{\"distance\": 2793, \"duration\": 540}]}")));
        GEO_APIS.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(okJson(
                "{\"current\": {\"temperature_2m\": -6.0, \"rain\": 0, \"showers\": 0, \"snowfall\": 0.4, \"weather_code\": 71}}")));
    }

    @Test
    void quotesRouteWithSnowSurcharge() {
        assertThat(quote(9101, 56.4700, 84.9700))
                .hasStatusOk()
                .bodyJson()
                .satisfies(body -> {
                    assertThat(body).extractingPath("$.distanceMeters").isEqualTo(2793);
                    assertThat(body).extractingPath("$.etaMinutes").isEqualTo(12);
                    assertThat(body).extractingPath("$.deliveryFee").isEqualTo(190.0);
                    assertThat(body).extractingPath("$.routeSource").isEqualTo("OSRM");
                    assertThat(body).extractingPath("$.weather.condition").isEqualTo("SNOW");
                    assertThat(body).extractingPath("$.weather.extraMinutes").isEqualTo(3);
                });
    }

    @Test
    void estimatesByStraightLineWhenOsrmIsDown() {
        GEO_APIS.stubFor(get(urlPathMatching("/route/v1/car/.*")).willReturn(aResponse().withStatus(503)));

        assertThat(quote(9102, 56.4720, 84.9720))
                .hasStatusOk()
                .bodyJson().extractingPath("$.routeSource").isEqualTo("STRAIGHT_LINE");
    }

    @Test
    void rejectsCustomerOutsideDeliveryZone() {
        assertThat(quote(9103, 56.6100, 85.2000))
                .hasStatus(422)
                .bodyJson().extractingPath("$.code").isEqualTo("OUT_OF_DELIVERY_ZONE");
    }

    @Test
    void quoteNeedsInternalToken() {
        assertThat(mvc.post().uri(QUOTE).header("X-Internal-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body(9104, 56.47, 84.97)))
                .hasStatus(401);
    }

    private MvcTestResult quote(long restaurantId, double latitude, double longitude) {
        return mvc.post().uri(QUOTE).contentType(MediaType.APPLICATION_JSON)
                .content(body(restaurantId, latitude, longitude)).exchange();
    }

    private static String body(long restaurantId, double latitude, double longitude) {
        return "{\"restaurantId\": " + restaurantId + ", \"from\": {\"latitude\": 56.4846, \"longitude\": 84.9476},"
                + " \"to\": {\"latitude\": " + latitude + ", \"longitude\": " + longitude + "}}";
    }
}
