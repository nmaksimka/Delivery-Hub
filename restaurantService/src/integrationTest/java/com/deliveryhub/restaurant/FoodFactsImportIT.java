package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.NutriScore;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class FoodFactsImportIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final WireMockServer FOOD_FACTS = new WireMockServer(options().dynamicPort());

    static {
        FOOD_FACTS.start();
    }

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void foodFactsIsWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.foodfacts.base-url", FOOD_FACTS::baseUrl);
        registry.add("app.foodfacts.requests-per-second", () -> 200);
        registry.add("app.foodfacts.retry-backoff", () -> "10ms");
        registry.add("app.foodfacts.products-per-category", () -> 5);
    }

    @AfterAll
    static void stopFoodFacts() {
        FOOD_FACTS.stop();
    }

    @BeforeEach
    void stubFoodFacts() {
        FOOD_FACTS.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        menuItemRepository.deleteAll();
        restaurantRepository.deleteAll();
        FOOD_FACTS.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(okJson("{\"products\": []}")));
        stubCategory("sodas", """
                {"products": [
                  {"code": "5449000000996", "product_name": "Coca-Cola", "brands": "Coca-Cola, Other",
                   "quantity": "330 ml", "nutriscore_grade": "e", "image_front_url": "https://img.example/cola.jpg",
                   "nutriments": {"energy-kcal_100g": 42, "proteins_100g": 0, "fat_100g": 0, "carbohydrates_100g": 10.6}},
                  {"code": "no-name", "product_name": " "}]}
                """);
        stubCategory("waters", """
                {"products": [{"code": "3068320055008", "product_name": "Evian", "product_name_ru": "Эвиан",
                   "nutriscore_grade": "a", "nutriments": {"energy-kcal_100g": 0}}]}
                """);
        stubCategory("dairies", """
                {"products": [
                  {"code": "3033490004743", "product_name": "Yogurt", "brands": "Danone", "quantity": "125 g",
                   "nutriscore_grade": "unknown",
                   "nutriments": {"energy-kcal_100g": 85.55, "proteins_100g": 4.1, "fat_100g": 3.2, "carbohydrates_100g": 9.9}},
                  {"code": "5449000000996", "product_name": "Duplicate of cola"}]}
                """);
    }

    @Test
    void importsProductsAndDrinksIntoSectionsOfTwoRestaurants() {
        MvcTestResult finished = awaitFinished(startImport());

        assertThat(finished).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(finished).bodyJson().extractingPath("$.source").isEqualTo("OPENFOODFACTS");
        assertThat(finished).bodyJson().extractingPath("$.total").isEqualTo(4);
        assertThat(finished).bodyJson().extractingPath("$.itemsCreated").isEqualTo(3);
        assertThat(finished).bodyJson().extractingPath("$.restaurantsCreated").isEqualTo(2);

        Restaurant bar = restaurantRepository.findByExternalSourceAndExternalId("OPENFOODFACTS", "BAR").orElseThrow();
        Restaurant grocery = restaurantRepository.findByExternalSourceAndExternalId("OPENFOODFACTS", "GROCERY")
                .orElseThrow();
        assertThat(bar.getCity()).isEqualTo("Томск");
        assertThat(bar.isActive()).isTrue();

        List<MenuItem> drinks = menuItemRepository.findByRestaurantIdAndSection(bar.getId(), MenuSection.DRINK);
        assertThat(drinks).extracting(MenuItem::getName).containsExactlyInAnyOrder("Coca-Cola", "Эвиан");
        MenuItem cola = drinks.stream().filter(item -> item.getName().equals("Coca-Cola")).findFirst().orElseThrow();
        assertThat(cola.getCategory()).isEqualTo("Газировка");
        assertThat(cola.getDescription()).isEqualTo("Coca-Cola, 330 ml");
        assertThat(cola.getImageUrl()).isEqualTo("https://img.example/cola.jpg");
        assertThat(cola.getNutrition().energyKcal()).isEqualByComparingTo("42.0");
        assertThat(cola.getNutrition().carbsGrams()).isEqualByComparingTo("10.6");
        assertThat(cola.getNutrition().nutriScore()).isEqualTo(NutriScore.E);
        assertThat(cola.getPrice().signum()).isPositive();

        List<MenuItem> products = menuItemRepository.findByRestaurantIdAndSection(grocery.getId(),
                MenuSection.PRODUCT);
        assertThat(products).singleElement().satisfies(yogurt -> {
            assertThat(yogurt.getNutrition().energyKcal()).isEqualByComparingTo("85.6");
            assertThat(yogurt.getNutrition().nutriScore()).isNull();
            assertThat(yogurt.getCategory()).isEqualTo("Молочные продукты");
        });
        assertThat(menuItemRepository.findByRestaurantIdAndSection(grocery.getId(), MenuSection.DRINK)).isEmpty();
    }

    @Test
    void menuEndpointFiltersBySectionAndExposesNutrition() {
        awaitFinished(startImport());
        Restaurant bar = restaurantRepository.findByExternalSourceAndExternalId("OPENFOODFACTS", "BAR").orElseThrow();

        assertThat(mvc.get().uri("/api/restaurants/" + bar.getId() + "/menu?section=DRINK")).hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(2);
        assertThat(mvc.get().uri("/api/restaurants/" + bar.getId() + "/menu?section=PRODUCT")).hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
        MvcTestResult menu = mvc.get().uri("/api/restaurants/" + bar.getId() + "/menu").exchange();
        assertThat(menu).hasStatusOk();
        List<String> scores = JsonPath.read(bodyOf(menu), "$[*].nutrition.nutriScore");
        assertThat(scores).containsExactlyInAnyOrder("E", "A");
        assertThat(JsonPath.<List<String>>read(bodyOf(menu), "$[*].section")).containsOnly("DRINK");
    }

    @Test
    void repeatedImportUpdatesItemsInsteadOfDuplicatingThemAndUsesCachedResponses() {
        awaitFinished(startImport());
        long items = menuItemRepository.count();

        MvcTestResult second = awaitFinished(startImport());

        assertThat(second).bodyJson().extractingPath("$.itemsCreated").isEqualTo(0);
        assertThat(second).bodyJson().extractingPath("$.itemsUpdated").isEqualTo(3);
        assertThat(second).bodyJson().extractingPath("$.restaurantsCreated").isEqualTo(0);
        assertThat(menuItemRepository.count()).isEqualTo(items);
        FOOD_FACTS.verify(1, getRequestedFor(urlPathEqualTo("/api/v2/search"))
                .withQueryParam("categories_tags_en", equalTo("sodas")));
    }

    @Test
    void jobFailsWithReasonWhenApiIsDown() {
        FOOD_FACTS.stubFor(get(urlPathEqualTo("/api/v2/search")).willReturn(aResponse().withStatus(503)));
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        MvcTestResult finished = awaitFinished(startImport());

        assertThat(finished).bodyJson().extractingPath("$.status").isEqualTo("FAILED");
        assertThat(finished).bodyJson().extractingPath("$.error").asString().contains("Open Food Facts");
    }

    private void stubCategory(String tag, String body) {
        FOOD_FACTS.stubFor(get(urlPathEqualTo("/api/v2/search"))
                .withQueryParam("categories_tags_en", equalTo(tag))
                .willReturn(okJson(body)));
    }

    private String startImport() {
        MvcTestResult started = mvc.post().uri("/api/admin/imports/products").exchange();
        assertThat(started).hasStatus(202);
        return JsonPath.read(bodyOf(started), "$.id");
    }

    private MvcTestResult awaitFinished(String jobId) {
        await().atMost(TIMEOUT).until(() -> {
            String status = JsonPath.read(bodyOf(mvc.get().uri("/api/admin/imports/" + jobId).exchange()), "$.status");
            return status.equals("COMPLETED") || status.equals("FAILED");
        });
        return mvc.get().uri("/api/admin/imports/" + jobId).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
