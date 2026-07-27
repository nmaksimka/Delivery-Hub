package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageStorage;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.lessThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, GarageContainerConfig.class, LocalImageSourceConfig.class})
class MealImportIT {

    private static final byte[] PNG = TestImages.png(1, 1);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final WireMockServer MEAL_DB = new WireMockServer(options().dynamicPort());

    static {
        MEAL_DB.start();
    }

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private ImageStorage imageStorage;

    @DynamicPropertySource
    static void mealDbIsWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.mealdb.base-url", MEAL_DB::baseUrl);
        registry.add("app.mealdb.requests-per-second", () -> 200);
        registry.add("app.mealdb.retry-backoff", () -> "10ms");
        registry.add("app.mealdb.meals-per-category", () -> 2);
    }

    @AfterAll
    static void stopMealDb() {
        MEAL_DB.stop();
    }

    @BeforeEach
    void stubMealDb() {
        MEAL_DB.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        MEAL_DB.stubFor(get("/categories.php").willReturn(okJson("""
                {"categories": [{"idCategory": "1", "strCategory": "Pasta"}, {"idCategory": "2", "strCategory": "Dessert"}]}
                """)));
        MEAL_DB.stubFor(get("/filter.php?c=Pasta").willReturn(okJson("""
                {"meals": [{"idMeal": "101", "strMeal": "Carbonara"}, {"idMeal": "102", "strMeal": "Lasagne"},
                           {"idMeal": "103", "strMeal": "Beyond the limit"}]}
                """)));
        MEAL_DB.stubFor(get("/filter.php?c=Dessert").willReturn(okJson("""
                {"meals": [{"idMeal": "201", "strMeal": "Mochi"}, {"idMeal": "202", "strMeal": "Ghost"}]}
                """)));
        stubMeal("101", "Carbonara", "Pasta", "Italian");
        stubMeal("102", "Lasagne", "Pasta", "Italian");
        stubMeal("201", "Mochi", "Dessert", "Japanese");
        MEAL_DB.stubFor(get("/lookup.php?i=202").willReturn(okJson("{\"meals\": null}")));
        MEAL_DB.stubFor(get(urlPathMatching("/images/.*")).willReturn(aResponse().withStatus(200).withBody(PNG)));
    }

    @Test
    void importsRestaurantsByCuisineWithMenuAndReportsProgress() {
        String jobId = startImport();

        MvcTestResult finished = awaitFinished(jobId);

        assertThat(finished).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(finished).bodyJson().extractingPath("$.total").isEqualTo(4);
        assertThat(finished).bodyJson().extractingPath("$.processed").isEqualTo(4);
        assertThat(finished).bodyJson().extractingPath("$.progressPercent").isEqualTo(100);
        assertThat(finished).bodyJson().extractingPath("$.itemsCreated").isEqualTo(3);

        Restaurant italian = restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Italian").orElseThrow();
        assertThat(italian.getName()).isEqualTo("Траттория «Белла Виста»");
        assertThat(italian.getCuisine()).isEqualTo("Итальянская");
        assertThat(italian.getCity()).isEqualTo("Томск");
        assertThat(italian.getAddress()).startsWith("г. Томск");
        assertThat(italian.getImageUrl()).isEqualTo(MEAL_DB.baseUrl() + "/images/101.jpg");
        assertThat(italian.getImageKey()).isEqualTo("mealdb/meals/101");
        assertThat(italian.isActive()).isTrue();

        List<MenuItem> menu = menuItemRepository.findByRestaurantId(italian.getId());
        assertThat(menu).extracting(MenuItem::getName).containsExactlyInAnyOrder("Carbonara", "Lasagne");
        assertThat(menu).allSatisfy(item -> {
            assertThat(item.getCategory()).isEqualTo("Паста");
            assertThat(item.getPrice()).isBetween(new BigDecimal("470.00"), new BigDecimal("580.00"));
            assertThat(item.getDescription()).isEqualTo("Cook it. Serve hot.");
            assertThat(item.getImageUrl()).startsWith(MEAL_DB.baseUrl() + "/images/");
            assertThat(item.getImageKey()).startsWith("mealdb/meals/");
            assertThat(imageStorage.exists(item.getImageKey())).isTrue();
            assertThat(item.isAvailable()).isTrue();
        });
        assertThat(restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Japanese")).isPresent();

        assertThat(mvc.get().uri("/api/restaurants/" + italian.getId() + "/menu")).hasStatusOk()
                .bodyJson().extractingPath("$[0].imageUrl").asString()
                .contains("/images/mealdb/meals/").contains("X-Amz-Signature=");
    }

    @Test
    void repeatedImportUpdatesItemsInsteadOfDuplicatingThemAndUsesCachedResponses() {
        awaitFinished(startImport());
        long restaurants = restaurantRepository.count();
        long items = menuItemRepository.count();

        MvcTestResult second = awaitFinished(startImport());

        assertThat(second).bodyJson().extractingPath("$.itemsCreated").isEqualTo(0);
        assertThat(second).bodyJson().extractingPath("$.itemsUpdated").isEqualTo(3);
        assertThat(second).bodyJson().extractingPath("$.restaurantsCreated").isEqualTo(0);
        assertThat(restaurantRepository.count()).isEqualTo(restaurants);
        assertThat(menuItemRepository.count()).isEqualTo(items);
        MEAL_DB.verify(1, getRequestedFor(urlEqualTo("/categories.php")));
        MEAL_DB.verify(1, getRequestedFor(urlEqualTo("/lookup.php?i=101")));
        MEAL_DB.verify(lessThanOrExactly(1), getRequestedFor(urlEqualTo("/images/101.jpg")));
    }

    @Test
    void jobFailsWithReasonWhenApiIsDown() {
        MEAL_DB.stubFor(get("/categories.php").willReturn(aResponse().withStatus(503)));

        MvcTestResult finished = awaitFinished(startImport());

        assertThat(finished).bodyJson().extractingPath("$.status").isEqualTo("FAILED");
        assertThat(finished).bodyJson().extractingPath("$.error").asString().contains("TheMealDB");
        assertThat(mvc.get().uri("/api/admin/imports").header("X-User-Role", "ADMIN")).hasStatusOk()
                .bodyJson().extractingPath("$[0].status").isEqualTo("FAILED");
    }

    @Test
    void unknownJobIsNotFound() {
        assertThat(mvc.get().uri("/api/admin/imports/00000000-0000-0000-0000-000000000000")
                .header("X-User-Role", "ADMIN")).hasStatus(404);
    }

    private String startImport() {
        MvcTestResult started = mvc.post().uri("/api/admin/imports/meals").header("X-User-Role", "ADMIN").exchange();
        assertThat(started).hasStatus(202);
        return JsonPath.read(bodyOf(started), "$.id");
    }

    private MvcTestResult awaitFinished(String jobId) {
        await().atMost(TIMEOUT).until(() -> {
            String status = JsonPath.read(bodyOf(mvc.get().uri("/api/admin/imports/" + jobId)
                    .header("X-User-Role", "ADMIN").exchange()), "$.status");
            return status.equals("COMPLETED") || status.equals("FAILED");
        });
        return mvc.get().uri("/api/admin/imports/" + jobId).header("X-User-Role", "ADMIN").exchange();
    }

    private static void stubMeal(String id, String name, String category, String area) {
        MEAL_DB.stubFor(get("/lookup.php?i=" + id).willReturn(okJson("""
                {"meals": [{"idMeal": "%s", "strMeal": "%s", "strCategory": "%s", "strArea": "%s",
                  "strInstructions": "Cook it.\\r\\nServe hot.", "strMealThumb": "%s/images/%s.jpg"}]}
                """.formatted(id, name, category, area, MEAL_DB.baseUrl(), id))));
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
