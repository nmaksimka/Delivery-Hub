package com.deliveryhub.search;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CatalogDishPayload;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@AutoConfigureMockMvc
@Import(SearchContainersConfig.class)
class CatalogSearchIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void findsDishByAnotherWordFormAndTypo() throws Exception {
        publish(snapshot(1L, "Сибирская кухня", true, Instant.parse("2026-07-01T10:00:00Z"),
                dish(11L, "Пельмени домашние", "Сочные пельмени со сметаной", true),
                dish(12L, "Борщ", "Наваристый, со свёклой", true)));

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "пельменями"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem("Пельмени домашние"))));

        mvc.perform(get("/api/search").param("q", "боршь").param("kind", "DISH"))
                .andExpect(jsonPath("$.items[0].name").value("Борщ"))
                .andExpect(jsonPath("$.items[0].restaurantName").value("Сибирская кухня"));
        mvc.perform(get("/api/search").param("q", "свекла"))
                .andExpect(jsonPath("$.items[*].name", hasItem("Борщ")));
    }

    @Test
    void russianQueryFindsDishImportedInEnglish() throws Exception {
        publish(snapshot(5L, "Meal Import", true, Instant.parse("2026-07-01T10:00:00Z"),
                dish(51L, "Chicken Curry", "Spicy chicken with rice", true),
                dish(52L, "Beef Stew", null, true),
                dish(53L, "Tomato Soup", null, true)));

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "курица"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem("Chicken Curry"))));

        mvc.perform(get("/api/search").param("q", "говядина").param("kind", "DISH"))
                .andExpect(jsonPath("$.items[0].name").value("Beef Stew"));
        mvc.perform(get("/api/search").param("q", "томатный суп").param("kind", "DISH"))
                .andExpect(jsonPath("$.items[0].name").value("Tomato Soup"));
    }

    @Test
    void searchFindsDishByPrefix() throws Exception {
        publish(snapshot(6L, "Пекарня", true, Instant.parse("2026-07-01T10:00:00Z"),
                dish(61L, "Круассан миндальный", null, true)));

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "круа"))
                .andExpect(jsonPath("$.items[*].name", hasItem("Круассан миндальный"))));
    }

    @Test
    void suggestsByPrefix() throws Exception {
        publish(snapshot(2L, "Блинная на Ленина", true, Instant.parse("2026-07-01T10:00:00Z"),
                dish(21L, "Блины с икрой", null, true),
                dish(22L, "Блинчики с творогом", null, true)));

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search/suggest").param("q", "блин"))
                .andExpect(jsonPath("$[*].text", hasItem("Блины с икрой")))
                .andExpect(jsonPath("$[*].text", hasItem("Блинная на Ленина"))));
    }

    @Test
    void newerSnapshotReplacesDishesAndOlderOneIsIgnored() throws Exception {
        Instant first = Instant.parse("2026-07-01T10:00:00Z");
        publish(snapshot(3L, "Шашлычная", true, first, dish(31L, "Шашлык из свинины", null, true)));
        publish(snapshot(3L, "Шашлычная", true, first.plusSeconds(60),
                dish(32L, "Люля-кебаб", null, true),
                dish(31L, "Шашлык из свинины", null, false)));
        publish(snapshot(3L, "Шашлычная", true, first, dish(31L, "Шашлык из свинины", null, true)));

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "кебаб"))
                .andExpect(jsonPath("$.items[*].name", hasItem("Люля-кебаб"))));
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() ->
                mvc.perform(get("/api/search").param("q", "шашлык").param("kind", "DISH"))
                        .andExpect(jsonPath("$.items[*].name", not(hasItem("Шашлык из свинины")))));
    }

    @Test
    void removedRestaurantDisappearsFromSearch() throws Exception {
        Instant first = Instant.parse("2026-07-01T10:00:00Z");
        publish(snapshot(4L, "Суши Томск", true, first, dish(41L, "Ролл Филадельфия", null, true)));
        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "филадельфия"))
                .andExpect(jsonPath("$.items", hasSize(1))));

        publish(RestaurantCatalogChangedEvent.newBuilder(snapshot(4L, "Суши Томск", false, first.plusSeconds(5)))
                .setRemoved(true)
                .build());

        await().atMost(TIMEOUT).untilAsserted(() -> mvc.perform(get("/api/search").param("q", "суши"))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("Суши Томск")))));
        mvc.perform(get("/api/search").param("q", "филадельфия")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mvc.perform(get("/api/search").param("q", " ")).andExpect(status().isBadRequest());
    }

    private void publish(RestaurantCatalogChangedEvent event) {
        kafkaTemplate.send(Topics.RESTAURANT_CATALOG_CHANGED, String.valueOf(event.getRestaurantId()), event).join();
    }

    private static RestaurantCatalogChangedEvent snapshot(long restaurantId, String name, boolean active,
                                                          Instant changedAt, CatalogDishPayload... dishes) {
        return RestaurantCatalogChangedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setRestaurantId(restaurantId)
                .setRemoved(false)
                .setName(name)
                .setActive(active)
                .setDishes(List.of(dishes))
                .setChangedAt(changedAt)
                .build();
    }

    private static CatalogDishPayload dish(long id, String name, String description, boolean available) {
        return CatalogDishPayload.newBuilder()
                .setMenuItemId(id)
                .setName(name)
                .setDescription(description)
                .setSection("DISH")
                .setPrice(new BigDecimal("350.00"))
                .setAvailable(available)
                .build();
    }
}
