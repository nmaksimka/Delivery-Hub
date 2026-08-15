package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.GeoLocation;
import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalClientsTest {

    private static final WireMockServer SERVICES = new WireMockServer(options().dynamicPort());
    private static final String ADDRESS_URL = "/internal/users/42/addresses/9";
    private static final GeoLocation RESTAURANT = new GeoLocation(56.4884, 84.948);

    private UserAddresses userAddresses;
    private DeliveryQuotes deliveryQuotes;

    @BeforeAll
    static void start() {
        SERVICES.start();
    }

    @AfterAll
    static void stop() {
        SERVICES.stop();
    }

    @BeforeEach
    void setUp() {
        SERVICES.resetAll();
        InternalServicesConfig config = new InternalServicesConfig(new InternalServicesProperties(SERVICES.baseUrl(),
                SERVICES.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1)), "internal-secret");
        userAddresses = config.userAddresses(RestClient.builder());
        deliveryQuotes = config.deliveryQuotes(RestClient.builder());
    }

    @Test
    void readsAddressWithInternalToken() {
        SERVICES.stubFor(get(urlEqualTo(ADDRESS_URL)).willReturn(okJson(address(true))));

        UserAddressDto address = userAddresses.deliverableAddress(42L, 9L);

        assertThat(address.line()).isEqualTo("Томск, Ленина, 12, кв. 4");
        assertThat(address.latitude()).isEqualByComparingTo("56.47");
        SERVICES.verify(getRequestedFor(urlEqualTo(ADDRESS_URL)).withHeader("X-Internal-Token", equalTo("internal-secret")));
    }

    @Test
    void addressWithoutCoordinatesCannotBeDeliveredTo() {
        SERVICES.stubFor(get(urlEqualTo(ADDRESS_URL)).willReturn(okJson(address(false))));

        assertCode(() -> userAddresses.deliverableAddress(42L, 9L), ErrorCode.ADDRESS_NOT_GEOCODED);
    }

    @Test
    void foreignOrMissingAddressIsNotFound() {
        SERVICES.stubFor(get(urlEqualTo(ADDRESS_URL)).willReturn(aResponse().withStatus(404)));

        assertCode(() -> userAddresses.deliverableAddress(42L, 9L), ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void userServiceOutageIsReportedAsUnavailable() {
        SERVICES.stubFor(get(urlEqualTo(ADDRESS_URL)).willReturn(aResponse().withStatus(503)));

        assertCode(() -> userAddresses.deliverableAddress(42L, 9L), ErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    void asksDeliveryServiceForQuoteFromRestaurantToAddress() {
        SERVICES.stubFor(post(urlEqualTo("/internal/delivery/quote"))
                .withRequestBody(equalToJson("{\"restaurantId\": 1, \"from\": {\"latitude\": 56.4884, \"longitude\": 84.948},"
                        + " \"to\": {\"latitude\": 56.47, \"longitude\": 84.97}}"))
                .willReturn(okJson("{\"distanceMeters\": 2793, \"etaMinutes\": 12, \"deliveryFee\": 190.00,"
                        + " \"routeSource\": \"OSRM\", \"weather\": {\"condition\": \"SNOW\", \"temperatureCelsius\": -6.0,"
                        + " \"surcharge\": 49.00, \"extraMinutes\": 3}}")));

        var quote = deliveryQuotes.quote(1L, RESTAURANT, addressDto());

        assertThat(quote.deliveryFee()).isEqualByComparingTo("190.00");
        assertThat(quote.weather().condition()).isEqualTo("SNOW");
    }

    @Test
    void addressOutsideZoneIsUnprocessable() {
        SERVICES.stubFor(post(urlEqualTo("/internal/delivery/quote")).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\": 422, \"code\": \"OUT_OF_DELIVERY_ZONE\"}")));

        assertCode(() -> deliveryQuotes.quote(1L, RESTAURANT, addressDto()), ErrorCode.OUT_OF_DELIVERY_ZONE);
    }

    @Test
    void restaurantWithoutLocationCannotQuote() {
        assertCode(() -> deliveryQuotes.quote(1L, null, addressDto()), ErrorCode.RESTAURANT_LOCATION_UNKNOWN);
    }

    @Test
    void deliveryServiceOutageIsReportedAsUnavailable() {
        SERVICES.stubFor(post(urlEqualTo("/internal/delivery/quote")).willReturn(aResponse().withStatus(500)));

        assertCode(() -> deliveryQuotes.quote(1L, RESTAURANT, addressDto()), ErrorCode.DELIVERY_UNAVAILABLE);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static UserAddressDto addressDto() {
        return new UserAddressDto(9L, "Томск", "Ленина", "12", "4", null, null, null,
                new BigDecimal("56.47"), new BigDecimal("84.97"), true);
    }

    private static String address(boolean geocoded) {
        return "{\"id\": 9, \"label\": \"Дом\", \"city\": \"Томск\", \"street\": \"Ленина\", \"house\": \"12\","
                + " \"apartment\": \"4\", \"latitude\": " + (geocoded ? "56.47" : "null") + ", \"longitude\": "
                + (geocoded ? "84.97" : "null") + ", \"geocoded\": " + geocoded + ", \"primary\": true}";
    }
}
