package com.deliveryhub.order;

import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.RestaurantCircuitOpenException;
import com.deliveryhub.order.client.RestaurantServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class RestaurantClientResilienceIT {

    private static final String RESTAURANT_URL = "/api/restaurants/1";
    private static final String RESTAURANT_JSON = """
            {"id": 1, "name": "Pizzeria", "active": true}
            """;
    private static final WireMockServer RESTAURANT_SERVICE = new WireMockServer(options().dynamicPort());

    static {
        RESTAURANT_SERVICE.start();
    }

    @Autowired
    private RestaurantCatalog restaurantCatalog;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private MockMvcTester mvc;

    @DynamicPropertySource
    static void restaurantServiceAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.restaurantService[0].uri",
                RESTAURANT_SERVICE::baseUrl);
        registry.add("resilience4j.timelimiter.configs.restaurantService.timeout-duration", () -> "700ms");
        registry.add("resilience4j.retry.instances.restaurantService.wait-duration", () -> "20ms");
    }

    @AfterAll
    static void stopRestaurantService() {
        RESTAURANT_SERVICE.stop();
    }

    @BeforeEach
    void resetState() {
        RESTAURANT_SERVICE.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void findsRestaurantServiceByNameAndReadsRestaurant() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).willReturn(okJson(RESTAURANT_JSON)));

        assertThat(restaurantCatalog.restaurant(1L).name()).isEqualTo("Pizzeria");
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void repeatsReadAfterServerErrorsUntilItSucceeds() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("second attempt"));
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).inScenario("flaky")
                .whenScenarioStateIs("second attempt")
                .willReturn(serverError())
                .willSetStateTo("recovered"));
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson(RESTAURANT_JSON)));

        assertThat(restaurantCatalog.restaurant(1L).active()).isTrue();
        assertThat(requestCount()).isEqualTo(3);
    }

    @Test
    void missingRestaurantIsNeitherRetriedNorCountedAsFailure() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).willReturn(aResponse().withStatus(404)));

        for (int call = 0; call < 6; call++) {
            assertThatThrownBy(() -> restaurantCatalog.restaurant(1L)).isInstanceOf(EntityNotFoundException.class);
        }

        assertThat(requestCount()).isEqualTo(6);
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
                .allSatisfy(breaker -> assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
    }

    @Test
    void answersServiceUnavailableProblemWhenRestaurantServiceFails() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).willReturn(serverError()));

        MvcTestResult result = mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"restaurantId": 1, "orderItemsRequest": [{"menuItemId": 10, "quantity": 2}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(503).hasHeader(HttpHeaders.RETRY_AFTER, "10");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Restaurant service unavailable");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(503);
        assertThat(requestCount()).isEqualTo(3);
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndStopsCallingRestaurantService() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL).willReturn(serverError()));

        assertThatThrownBy(() -> restaurantCatalog.restaurant(1L))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        assertThatThrownBy(() -> restaurantCatalog.restaurant(1L))
                .isInstanceOf(RestaurantServiceUnavailableException.class);
        int requestsBeforeOpenCircuit = requestCount();

        assertThatThrownBy(() -> restaurantCatalog.restaurant(1L))
                .isInstanceOf(RestaurantCircuitOpenException.class);

        assertThat(requestsBeforeOpenCircuit).isEqualTo(5);
        assertThat(requestCount()).isEqualTo(requestsBeforeOpenCircuit);
    }

    @Test
    void givesUpOnResponseSlowerThanTimeLimit() {
        RESTAURANT_SERVICE.stubFor(get(RESTAURANT_URL)
                .willReturn(okJson(RESTAURANT_JSON).withFixedDelay(900)));

        assertThatThrownBy(() -> restaurantCatalog.restaurant(1L))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class)
                .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    private static int requestCount() {
        return RESTAURANT_SERVICE.findAll(getRequestedFor(urlEqualTo(RESTAURANT_URL))).size();
    }
}
