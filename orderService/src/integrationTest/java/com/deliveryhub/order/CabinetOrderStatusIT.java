package com.deliveryhub.order;

import com.deliveryhub.order.client.RestaurantOwnership;
import com.deliveryhub.order.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class,
        RedisContainerConfig.class})
class CabinetOrderStatusIT {

    private static final long RESTAURANT = 8001L;
    private static final long OTHER_RESTAURANT = 8002L;
    private static final long OWNER = 51L;

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private RestaurantOwnership ownership;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM order_items").update();
        jdbcClient.sql("DELETE FROM orders").update();
    }

    @Test
    void ownerHandsConfirmedOrderToDelivery() {
        long id = order(RESTAURANT, "CONFIRMED");

        MvcTestResult result = patch(id, "IN_DELIVERY");

        assertThat(result).hasStatus(200).bodyJson().extractingPath("$.status").isEqualTo("IN_DELIVERY");
        assertThat(statusOf(id)).isEqualTo("IN_DELIVERY");
    }

    @Test
    void ownerCancelsOrderBeforeDeliveryWithRestaurantReason() {
        long id = order(RESTAURANT, "CONFIRMED");

        MvcTestResult result = patch(id, "CANCELLED");

        assertThat(result).hasStatus(200).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(jdbcClient.sql("SELECT cancellation_reason FROM orders WHERE id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("RESTAURANT");
    }

    @Test
    void ownerCannotCancelOrderAlreadyInDelivery() {
        long id = order(RESTAURANT, "IN_DELIVERY");

        assertThat(patch(id, "CANCELLED")).hasStatus(409);
        assertThat(statusOf(id)).isEqualTo("IN_DELIVERY");
    }

    @Test
    void ownerCannotSkipStraightToDelivered() {
        long id = order(RESTAURANT, "CONFIRMED");

        assertThat(patch(id, "DELIVERED")).hasStatus(409);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void ownerCannotHandUnpaidOrderToDelivery() {
        long id = order(RESTAURANT, "PAYMENT_HELD");

        assertThat(patch(id, "IN_DELIVERY")).hasStatus(409);
        assertThat(statusOf(id)).isEqualTo("PAYMENT_HELD");
    }

    @Test
    void foreignRestaurantOrderIsForbidden() {
        long id = order(OTHER_RESTAURANT, "CONFIRMED");
        doThrow(new ForbiddenException("not yours")).when(ownership).requireOwner(OWNER, OTHER_RESTAURANT);

        assertThat(patch(id, "IN_DELIVERY")).hasStatus(403);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThat(patch(987654L, "IN_DELIVERY")).hasStatus(404);
    }

    private MvcTestResult patch(long id, String status) {
        return mvc.patch().uri("/api/cabinet/orders/" + id + "/status")
                .header("X-User-Id", Long.toString(OWNER)).header("X-User-Role", "RESTAURANT_OWNER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}").exchange();
    }

    private String statusOf(long id) {
        return jdbcClient.sql("SELECT status FROM orders WHERE id = :id").param("id", id)
                .query(String.class).single();
    }

    private long order(long restaurantId, String status) {
        return jdbcClient.sql("""
                        INSERT INTO orders (user_id, restaurant_id, status, total_amount, discount, delivery_fee,
                                            created_at, updated_at)
                        VALUES (1, :restaurant, :status, 100, 0, 0, now(), now())
                        RETURNING id
                        """)
                .param("restaurant", restaurantId).param("status", status).query(Long.class).single();
    }
}
