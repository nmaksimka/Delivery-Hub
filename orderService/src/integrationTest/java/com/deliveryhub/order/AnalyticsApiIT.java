package com.deliveryhub.order;

import com.deliveryhub.order.client.RestaurantOwnership;
import com.deliveryhub.order.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import java.util.List;
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
class AnalyticsApiIT {

    private static final long RESTAURANT = 7001L;
    private static final long OTHER_RESTAURANT = 7002L;
    private static final long OWNER = 42L;
    private static final String PERIOD = "from=2026-03-01&to=2026-03-04";

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private RestaurantOwnership ownership;

    @BeforeEach
    void seedOrders() {
        jdbcClient.sql("DELETE FROM order_items").update();
        jdbcClient.sql("DELETE FROM orders").update();
        deliveredOrder(RESTAURANT, "2026-03-01T10:00:00+07", 30, "100.00", "Пицца", 2, "50.00");
        deliveredOrder(RESTAURANT, "2026-03-01T12:00:00+07", 50, "60.00", "Пицца", 1, "50.00");
        deliveredOrder(RESTAURANT, "2026-03-02T12:00:00+07", 40, "120.00", "Суши", 3, "40.00");
        deliveredOrder(RESTAURANT, "2026-03-04T19:00:00+07", 20, "90.00", "Суши", 1, "40.00");
        deliveredOrder(OTHER_RESTAURANT, "2026-03-02T13:00:00+07", 90, "500.00", "Бургер", 5, "100.00");
        cancelledOrder(RESTAURANT, "2026-03-02T14:00:00+07", "999.00");
    }

    @Test
    void revenueByDayHasRunningTotalMovingAverageAndChange() {
        MvcTestResult result = admin("restaurantId=" + RESTAURANT + "&" + PERIOD);

        assertThat(result).hasStatus(200).bodyJson()
                .hasPathSatisfying("$.revenueByDay.length()", path -> path.assertThat().isEqualTo(3));
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[0].day").isEqualTo("2026-03-01");
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[0].orders").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[0].revenue").isEqualTo(160.0);
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[1].cumulative").isEqualTo(280.0);
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[1].change").isEqualTo(-40.0);
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[2].cumulative").isEqualTo(370.0);
        assertThat(result).bodyJson().extractingPath("$.revenueByDay[2].movingAverage").isEqualTo(123.33);
    }

    @Test
    void topDishesAreRankedByQuantityWithTiesSharingRank() {
        MvcTestResult result = admin("restaurantId=" + RESTAURANT + "&" + PERIOD);

        assertThat(result).bodyJson().extractingPath("$.topDishes[0].name").isEqualTo("Суши");
        assertThat(result).bodyJson().extractingPath("$.topDishes[0].quantity").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.topDishes[0].rank").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.topDishes[1].name").isEqualTo("Пицца");
        assertThat(result).bodyJson().extractingPath("$.topDishes[1].rank").isEqualTo(2);
    }

    @Test
    void deliveryTimeUsesOnlyDeliveredOrdersAndShowsPercentiles() {
        MvcTestResult result = admin("restaurantId=" + RESTAURANT + "&" + PERIOD);

        assertThat(result).bodyJson().extractingPath("$.deliveryTime.orders").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.deliveryTime.averageMinutes").isEqualTo(35.0);
        assertThat(result).bodyJson().extractingPath("$.deliveryTime.medianMinutes").isEqualTo(35.0);
        assertThat(result).bodyJson().extractingPath("$.deliveryTimeByDay[0].averageMinutes").isEqualTo(40.0);
    }

    @Test
    void adminWithoutRestaurantSeesAllRestaurants() {
        MvcTestResult result = admin(PERIOD);

        assertThat(result).bodyJson().extractingPath("$.revenueByDay[1].revenue").isEqualTo(620.0);
        assertThat(result).bodyJson().extractingPath("$.topDishes[0].name").isEqualTo("Бургер");
    }

    @Test
    void emptyPeriodGivesEmptyReport() {
        MvcTestResult result = admin("from=2025-01-01&to=2025-01-03");

        assertThat(result).hasStatus(200).bodyJson().extractingPath("$.revenueByDay").isEqualTo(List.of());
        assertThat(result).bodyJson().extractingPath("$.deliveryTime").isNull();
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThat(admin("from=2026-03-05&to=2026-03-01")).hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        assertThat(admin("from=2020-01-01&to=2026-03-01")).hasStatus(400);
    }

    @Test
    void adminEndpointRejectsNonAdmins() {
        assertThat(mvc.get().uri("/api/admin/analytics?" + PERIOD).header("X-User-Role", "USER").exchange())
                .hasStatus(403);
    }

    @Test
    void ownerSeesOwnRestaurantOnly() {
        assertThat(owner(RESTAURANT)).hasStatus(200).bodyJson()
                .extractingPath("$.restaurantId").isEqualTo((int) RESTAURANT);

        doThrow(new ForbiddenException("not yours")).when(ownership).requireOwner(OWNER, OTHER_RESTAURANT);

        assertThat(owner(OTHER_RESTAURANT)).hasStatus(403);
    }

    private MvcTestResult admin(String query) {
        return mvc.get().uri("/api/admin/analytics?" + query).header("X-User-Role", "ADMIN").exchange();
    }

    private MvcTestResult owner(long restaurantId) {
        return mvc.get().uri("/api/cabinet/analytics?restaurantId=" + restaurantId + "&" + PERIOD)
                .header("X-User-Id", Long.toString(OWNER)).header("X-User-Role", "RESTAURANT_OWNER").exchange();
    }

    private void deliveredOrder(long restaurantId, String createdAt, int minutes, String total, String dish,
                                int quantity, String price) {
        Long id = jdbcClient.sql("""
                        INSERT INTO orders (user_id, restaurant_id, status, total_amount, discount, delivery_fee,
                                            created_at, updated_at)
                        VALUES (1, :restaurant, 'DELIVERED', CAST(:total AS NUMERIC), 0, 0,
                                CAST(:createdAt AS TIMESTAMPTZ),
                                CAST(:createdAt AS TIMESTAMPTZ) + make_interval(mins => :minutes))
                        RETURNING id
                        """)
                .param("restaurant", restaurantId).param("total", total).param("createdAt", createdAt)
                .param("minutes", minutes).query(Long.class).single();
        jdbcClient.sql("""
                        INSERT INTO order_items (order_id, menu_item_id, item_name, quantity, price_per_unit)
                        VALUES (:order, :menuItem, :dish, :quantity, CAST(:price AS NUMERIC))
                        """)
                .param("order", id).param("menuItem", Math.abs(dish.hashCode() % 1000)).param("dish", dish)
                .param("quantity", quantity).param("price", price).update();
    }

    private void cancelledOrder(long restaurantId, String createdAt, String total) {
        jdbcClient.sql("""
                        INSERT INTO orders (user_id, restaurant_id, status, total_amount, discount, delivery_fee,
                                            created_at, updated_at, cancellation_reason)
                        VALUES (1, :restaurant, 'CANCELLED', CAST(:total AS NUMERIC), 0, 0,
                                CAST(:createdAt AS TIMESTAMPTZ), CAST(:createdAt AS TIMESTAMPTZ), 'UNKNOWN')
                        """)
                .param("restaurant", restaurantId).param("total", total).param("createdAt", createdAt).update();
    }
}
