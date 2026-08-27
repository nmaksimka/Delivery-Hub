package com.deliveryhub.restaurant;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class RestaurantRatingIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void newRestaurantHasNoRating() {
        long restaurantId = createRestaurant("Без отзывов");

        assertThat(mvc.get().uri("/api/restaurants/" + restaurantId)).hasStatusOk()
                .bodyJson().extractingPath("$.ratingCount").isEqualTo(0);
        assertThat(mvc.get().uri("/api/restaurants/" + restaurantId)).hasStatusOk()
                .bodyJson().extractingPath("$.rating").isNull();
    }

    @Test
    void reviewEventsUpdateAverageRating() {
        long restaurantId = createRestaurant("С отзывами");

        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), review(restaurantId, 5));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), review(restaurantId, 4));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(mvc.get().uri("/api/restaurants/" + restaurantId))
                        .bodyJson().extractingPath("$.ratingCount").isEqualTo(2));
        assertThat(mvc.get().uri("/api/restaurants/" + restaurantId))
                .bodyJson().extractingPath("$.rating").isEqualTo(4.5);
    }

    @Test
    void redeliveredEventIsCountedOnce() {
        long restaurantId = createRestaurant("Дубль");
        ReviewSubmittedEvent event = review(restaurantId, 3);

        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), event);
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), event);
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), review(restaurantId, 5));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(jdbcClient.sql("SELECT rating_count FROM restaurants WHERE id = :id")
                        .param("id", restaurantId).query(Integer.class).single()).isEqualTo(2));
        assertThat(jdbcClient.sql("SELECT rating_sum FROM restaurants WHERE id = :id")
                .param("id", restaurantId).query(Long.class).single()).isEqualTo(8L);
    }

    @Test
    void reviewOfUnknownRestaurantIsIgnored() {
        long restaurantId = createRestaurant("Живой");

        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "999999", review(999_999L, 5));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, Long.toString(restaurantId), review(restaurantId, 2));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(jdbcClient.sql("SELECT rating_count FROM restaurants WHERE id = :id")
                        .param("id", restaurantId).query(Integer.class).single()).isEqualTo(1));
    }

    private static ReviewSubmittedEvent review(long restaurantId, int rating) {
        return new ReviewSubmittedEvent(UUID.randomUUID(), 1L, 1L, 42L, restaurantId, rating, null, null, Instant.now());
    }

    private long createRestaurant(String name) {
        try {
            String body = mvc.post().uri("/api/restaurants")
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"address\": \"Ленина 1\"}")
                    .exchange().getResponse().getContentAsString();
            return ((Number) JsonPath.read(body, "$.id")).longValue();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
