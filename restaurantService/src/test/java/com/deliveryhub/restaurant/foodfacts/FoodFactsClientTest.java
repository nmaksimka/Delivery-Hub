package com.deliveryhub.restaurant.foodfacts;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodFactsClientTest {

    private static final FoodFactsProperties PROPERTIES = new FoodFactsProperties("http://unused", "test-agent",
            Duration.ofSeconds(1), Duration.ofSeconds(2), 100, Duration.ofSeconds(1), 3, Duration.ofMillis(10),
            Duration.ofHours(1), 10);

    private final WireMockServer foodFacts = new WireMockServer(options().dynamicPort());

    private FoodFactsClient client;

    @BeforeEach
    void setUp() {
        foodFacts.start();
        client = clientWith(FoodFactsConfig.rateLimiter(PROPERTIES), CircuitBreaker.ofDefaults("test"));
    }

    @AfterEach
    void tearDown() {
        foodFacts.stop();
    }

    @Test
    void readsPopularProductsWithNutritionAndSendsIdentifyingUserAgent() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search"))
                .withQueryParam("categories_tags_en", equalTo("sodas"))
                .withQueryParam("page_size", equalTo("5"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo("test-agent"))
                .willReturn(okJson("""
                        {"count": 1, "products": [{
                          "code": "5449000000996", "product_name": "Coca-Cola", "product_name_ru": "Кока-Кола",
                          "brands": "Coca-Cola, Other", "quantity": "330 ml", "nutriscore_grade": "e",
                          "image_front_url": "https://img/cola.jpg", "unknown_field": 1,
                          "nutriments": {"energy-kcal_100g": 42, "proteins_100g": 0, "fat_100g": 0,
                                         "carbohydrates_100g": "10.6"}}]}
                        """)));

        assertThat(client.popularInCategory("sodas", 5)).singleElement().satisfies(product -> {
            assertThat(product.code()).isEqualTo("5449000000996");
            assertThat(product.displayName()).isEqualTo("Кока-Кола");
            assertThat(product.nutriScoreGrade()).isEqualTo("e");
            assertThat(product.nutriments().energyKcal()).isEqualTo(42.0);
            assertThat(product.nutriments().carbohydrates()).isEqualTo(10.6);
        });
    }

    @Test
    void fallsBackToOriginalNameWhenNoRussianOne() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(okJson("""
                {"products": [{"code": "1", "product_name": " Milk ", "product_name_ru": ""}]}
                """)));

        assertThat(client.popularInCategory("dairies", 1)).singleElement()
                .extracting(FoodFactsProduct::displayName).isEqualTo("Milk");
    }

    @Test
    void missingProductsListIsEmpty() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(okJson("{\"count\": 0}")));

        assertThat(client.popularInCategory("nothing", 3)).isEmpty();
    }

    @Test
    void repeatsRequestWhenApiFailsTemporarily() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("{\"products\": []}")));

        assertThat(client.popularInCategory("waters", 3)).isEmpty();
        foodFacts.verify(2, getRequestedFor(urlPathEqualTo("/api/v2/search")));
    }

    @Test
    void givesUpAfterLastAttempt() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.popularInCategory("waters", 3))
                .isInstanceOf(FoodFactsUnavailableException.class);
        foodFacts.verify(3, getRequestedFor(urlPathEqualTo("/api/v2/search")));
    }

    @Test
    void unreadableBodyIsReportedAsUnavailable() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search"))
                .willReturn(aResponse().withStatus(200).withBody("<html>oops</html>")));

        assertThatThrownBy(() -> client.popularInCategory("waters", 3))
                .isInstanceOf(FoodFactsUnavailableException.class)
                .hasMessageContaining("unreadable");
    }

    @Test
    void doesNotRepeatOnClientError() {
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.popularInCategory("waters", 3))
                .isInstanceOf(FoodFactsUnavailableException.class);
        foodFacts.verify(1, getRequestedFor(urlPathEqualTo("/api/v2/search")));
    }

    @Test
    void openCircuitStopsCallingApi() {
        CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .recordExceptions(HttpServerErrorException.class)
                .build());
        client = clientWith(FoodFactsConfig.rateLimiter(PROPERTIES), breaker);
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.popularInCategory("waters", 3))
                .isInstanceOf(FoodFactsUnavailableException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = foodFacts.findAll(getRequestedFor(urlPathEqualTo("/api/v2/search"))).size();

        assertThatThrownBy(() -> client.popularInCategory("waters", 3))
                .isInstanceOf(FoodFactsUnavailableException.class);

        assertThat(foodFacts.findAll(getRequestedFor(urlPathEqualTo("/api/v2/search")))).hasSize(callsBefore);
    }

    @Test
    void requestsOverLimitAreRejectedWithoutReachingApi() {
        RateLimiter onePerMinute = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        client = clientWith(onePerMinute, CircuitBreaker.ofDefaults("test"));
        foodFacts.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(okJson("{\"products\": []}")));

        client.popularInCategory("waters", 1);

        assertThatThrownBy(() -> client.popularInCategory("sodas", 1))
                .isInstanceOf(FoodFactsUnavailableException.class);
        foodFacts.verify(1, getRequestedFor(urlPathEqualTo("/api/v2/search")));
    }

    private FoodFactsClient clientWith(RateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        FoodFactsHttp http = new FoodFactsHttp(RestClient.builder()
                .baseUrl(foodFacts.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, PROPERTIES.userAgent())
                .build(), rateLimiter, circuitBreaker, FoodFactsConfig.retry(PROPERTIES));
        return new FoodFactsClient(http, JsonMapper.builder().build());
    }
}
