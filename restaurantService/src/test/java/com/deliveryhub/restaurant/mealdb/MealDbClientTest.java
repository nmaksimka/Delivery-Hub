package com.deliveryhub.restaurant.mealdb;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealDbClientTest {

    private static final MealDbProperties PROPERTIES = new MealDbProperties("http://unused", Duration.ofSeconds(1),
            Duration.ofSeconds(2), 100, Duration.ofSeconds(1), 3, Duration.ofMillis(10), Duration.ofHours(1), 12);

    private final WireMockServer mealDb = new WireMockServer(options().dynamicPort());

    private MealDbClient client;

    @BeforeEach
    void setUp() {
        mealDb.start();
        client = clientWith(MealDbConfig.rateLimiter(PROPERTIES), CircuitBreaker.ofDefaults("test"));
    }

    @AfterEach
    void tearDown() {
        mealDb.stop();
    }

    @Test
    void readsCategories() {
        mealDb.stubFor(get("/categories.php").willReturn(okJson("""
                {"categories": [
                  {"idCategory": "1", "strCategory": "Beef", "strCategoryThumb": "https://img/beef.png"},
                  {"idCategory": "3", "strCategory": "Dessert", "strCategoryThumb": "https://img/dessert.png"}]}
                """)));

        assertThat(client.categories()).extracting(MealDbCategory::strCategory).containsExactly("Beef", "Dessert");
    }

    @Test
    void readsMealsOfCategoryAndEncodesItsName() {
        mealDb.stubFor(get(urlPathEqualTo("/filter.php")).withQueryParam("c", equalTo("Sea food")).willReturn(okJson("""
                {"meals": [{"strMeal": "Fish pie", "strMealThumb": "https://img/fish.jpg", "idMeal": "52802"}]}
                """)));

        assertThat(client.mealsOfCategory("Sea food")).singleElement()
                .satisfies(meal -> {
                    assertThat(meal.idMeal()).isEqualTo("52802");
                    assertThat(meal.strMeal()).isEqualTo("Fish pie");
                    assertThat(meal.strArea()).isNull();
                });
    }

    @Test
    void readsMealDetailsIgnoringUnknownFields() {
        mealDb.stubFor(get("/lookup.php?i=52772").willReturn(okJson("""
                {"meals": [{"idMeal": "52772", "strMeal": "Teriyaki Chicken Casserole", "strCategory": "Chicken",
                  "strArea": "Japanese", "strInstructions": "Preheat oven.", "strMealThumb": "https://img/t.jpg",
                  "strIngredient1": "soy sauce", "strYoutube": "https://youtube"}]}
                """)));

        assertThat(client.meal("52772")).get().satisfies(meal -> {
            assertThat(meal.strArea()).isEqualTo("Japanese");
            assertThat(meal.strCategory()).isEqualTo("Chicken");
            assertThat(meal.strMealThumb()).isEqualTo("https://img/t.jpg");
        });
    }

    @Test
    void unknownMealIsEmptyBecauseApiAnswersWithNullList() {
        mealDb.stubFor(get("/lookup.php?i=1").willReturn(okJson("{\"meals\": null}")));

        assertThat(client.meal("1")).isEmpty();
    }

    @Test
    void searchesByFirstLetter() {
        mealDb.stubFor(get("/search.php?f=a").willReturn(okJson("""
                {"meals": [{"idMeal": "1", "strMeal": "Apple Frangipan Tart"}, {"idMeal": "2", "strMeal": "Ayam Percik"}]}
                """)));

        assertThat(client.mealsStartingWith('a')).hasSize(2);
    }

    @Test
    void repeatsRequestWhenApiFailsTemporarily() {
        mealDb.stubFor(get("/categories.php").inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        mealDb.stubFor(get("/categories.php").inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson("{\"categories\": []}")));

        assertThat(client.categories()).isEmpty();
        mealDb.verify(2, getRequestedFor(urlEqualTo("/categories.php")));
    }

    @Test
    void givesUpAfterLastAttempt() {
        mealDb.stubFor(get("/categories.php").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.categories()).isInstanceOf(MealDbUnavailableException.class);
        mealDb.verify(3, getRequestedFor(urlEqualTo("/categories.php")));
    }

    @Test
    void doesNotRepeatOnClientError() {
        mealDb.stubFor(get("/categories.php").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.categories()).isInstanceOf(MealDbUnavailableException.class);
        mealDb.verify(1, getRequestedFor(urlEqualTo("/categories.php")));
    }

    @Test
    void unreadableBodyIsReportedAsUnavailable() {
        mealDb.stubFor(get("/categories.php").willReturn(aResponse().withStatus(200).withBody("<html>oops</html>")));

        assertThatThrownBy(() -> client.categories())
                .isInstanceOf(MealDbUnavailableException.class)
                .hasMessageContaining("unreadable");
    }

    @Test
    void openCircuitStopsCallingApi() {
        CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .recordExceptions(HttpServerErrorException.class)
                .build());
        client = clientWith(MealDbConfig.rateLimiter(PROPERTIES), breaker);
        mealDb.stubFor(get("/categories.php").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.categories()).isInstanceOf(MealDbUnavailableException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = mealDb.findAll(getRequestedFor(urlEqualTo("/categories.php"))).size();

        assertThatThrownBy(() -> client.categories()).isInstanceOf(MealDbUnavailableException.class);

        assertThat(mealDb.findAll(getRequestedFor(urlEqualTo("/categories.php")))).hasSize(callsBefore);
    }

    @Test
    void requestsOverLimitAreRejectedWithoutReachingApi() {
        RateLimiter onePerMinute = RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        client = clientWith(onePerMinute, CircuitBreaker.ofDefaults("test"));
        mealDb.stubFor(get("/categories.php").willReturn(okJson("{\"categories\": []}")));

        client.categories();

        assertThatThrownBy(() -> client.categories()).isInstanceOf(MealDbUnavailableException.class);
        mealDb.verify(1, getRequestedFor(urlEqualTo("/categories.php")));
    }

    private MealDbClient clientWith(RateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        Retry retry = MealDbConfig.retry(PROPERTIES);
        MealDbHttp http = new MealDbHttp(RestClient.builder().baseUrl(mealDb.baseUrl()).build(),
                rateLimiter, circuitBreaker, retry);
        return new MealDbClient(http, JsonMapper.builder().build());
    }
}
