package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.order.client.DeliveryQuotes;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.UserAddresses;
import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.client.dto.UserAddressDto;
import com.jayway.jsonpath.JsonPath;
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
import org.testcontainers.kafka.KafkaContainer;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class ReviewApiIT {

    private static final long COURIER_ID = 9001L;
    private static final String ORDER_REQUEST = """
            {"restaurantId": 1, "orderItemsRequest": [{"menuItemId": 10, "quantity": 2}], "addressId": 77}
            """;

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;
    @MockitoBean
    private UserAddresses userAddresses;
    @MockitoBean
    private DeliveryQuotes deliveryQuotes;

    @BeforeEach
    void stubDependencies() {
        when(userAddresses.deliverableAddress(any(), eq(77L))).thenReturn(new UserAddressDto(77L, "Томск", "Ленина",
                "12", null, null, null, null, new BigDecimal("56.47"), new BigDecimal("84.97"), true));
        when(deliveryQuotes.quote(any(), any(), any(UserAddressDto.class))).thenReturn(new DeliveryQuoteDto(1500, 20,
                new BigDecimal("0.00"), "OSRM", new DeliveryQuoteDto.Weather("CLEAR", 5.0, new BigDecimal("0.00"), 0)));
        when(restaurantCatalog.quote(eq(1L), any())).thenReturn(new MenuQuote(
                new RestaurantDto(1L, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true))));
    }

    @Test
    void reviewOfDeliveredOrderIsSavedAndPublished() {
        Integer orderId = deliveredOrder(501L);

        MvcTestResult created = submit(orderId, 501L, review(5, 4, "Горячая пицца, курьер быстрый"));

        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.courierId").isEqualTo((int) COURIER_ID);
        Optional<ReviewSubmittedEvent> published = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.REVIEW_SUBMITTED, ReviewSubmittedEvent.class, event -> event.getOrderId() == orderId);
        assertThat(published).isPresent();
        assertThat(published.get().getRestaurantId()).isEqualTo(1L);
        assertThat(published.get().getRestaurantRating()).isEqualTo(5);
        assertThat(published.get().getCourierId()).isEqualTo(COURIER_ID);
        assertThat(published.get().getCourierRating()).isEqualTo(4);
    }

    @Test
    void secondReviewOfSameOrderIsRejected() {
        Integer orderId = deliveredOrder(502L);
        assertThat(submit(orderId, 502L, review(5, null, "Хорошо"))).hasStatus(201);

        assertThat(submit(orderId, 502L, review(1, null, "Передумал")))
                .hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("REVIEW_ALREADY_EXISTS");
    }

    @Test
    void undeliveredOrderCannotBeReviewed() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(503L)), "$.id");

        assertThat(submit(orderId, 503L, review(5, null, "Рано")))
                .hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_DELIVERED");
    }

    @Test
    void strangerCannotReviewSomeoneElsesOrder() {
        Integer orderId = deliveredOrder(504L);

        assertThat(submit(orderId, 505L, review(5, null, "Чужой заказ"))).hasStatus(403);
    }

    @Test
    void ratingOutsideOneToFiveFailsValidation() {
        Integer orderId = deliveredOrder(506L);

        assertThat(submit(orderId, 506L, review(6, null, "Шесть")))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(submit(orderId, 506L, review(5, 0, "Курьер ноль")))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(submit(orderId, 506L, review(5, null, "  "))).hasStatus(400);
    }

    @Test
    void courierRatingNeedsCourierOnOrder() {
        Integer orderId = deliveredOrder(507L);
        jdbcClient.sql("UPDATE orders SET courier_id = NULL WHERE id = :id").param("id", orderId).update();

        assertThat(submit(orderId, 507L, review(5, 5, "Курьер неизвестен")))
                .hasStatus(422).bodyJson().extractingPath("$.code").isEqualTo("COURIER_NOT_ASSIGNED");
    }

    @Test
    void ownerReadsReviewOfOrderAndSeesItInMyReviews() {
        Integer orderId = deliveredOrder(508L);
        assertThat(mvc.get().uri("/api/orders/" + orderId + "/review").header("X-User-Id", "508"))
                .hasStatus(404).bodyJson().extractingPath("$.code").isEqualTo("REVIEW_NOT_FOUND");
        submit(orderId, 508L, review(4, 3, "Нормально"));

        assertThat(mvc.get().uri("/api/orders/" + orderId + "/review").header("X-User-Id", "508"))
                .hasStatusOk().bodyJson().extractingPath("$.restaurantRating").isEqualTo(4);
        assertThat(mvc.get().uri("/api/orders/" + orderId + "/review").header("X-User-Id", "509")).hasStatus(403);
        assertThat(mvc.get().uri("/api/orders/reviews/mine").header("X-User-Id", "508"))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].orderId").isEqualTo(orderId);
    }

    private Integer deliveredOrder(long userId) {
        Integer orderId = JsonPath.read(bodyOf(createOrder(userId)), "$.id");
        jdbcClient.sql("UPDATE orders SET status = 'DELIVERED', courier_id = :courier WHERE id = :id")
                .param("courier", COURIER_ID)
                .param("id", orderId)
                .update();
        return orderId;
    }

    private MvcTestResult createOrder(long userId) {
        return mvc.post().uri("/api/orders")
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ORDER_REQUEST)
                .exchange();
    }

    private MvcTestResult submit(Integer orderId, long userId, String body) {
        return mvc.post().uri("/api/orders/" + orderId + "/review")
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String review(int restaurantRating, Integer courierRating, String comment) {
        String courier = courierRating == null ? "null" : courierRating.toString();
        return "{\"restaurantRating\": " + restaurantRating + ", \"courierRating\": " + courier
                + ", \"comment\": \"" + comment + "\"}";
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
