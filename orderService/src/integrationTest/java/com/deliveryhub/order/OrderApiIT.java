package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class OrderApiIT {

    private static final long RESTAURANT_ID = 1L;
    private static final String ORDER_REQUEST = """
            {"restaurantId": 1, "orderItemsRequest": [
                {"menuItemId": 10, "quantity": 2},
                {"menuItemId": 11, "quantity": 3}]}
            """;

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private KafkaContainer kafka;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;

    @BeforeEach
    void stubRestaurantService() {
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any())).thenReturn(new MenuQuote(
                new RestaurantDto(RESTAURANT_ID, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true),
                        11L, new MenuItemDto(11L, "Cola", new BigDecimal("3.00"), true))));
    }

    @Test
    void createsOrderAndPublishesOrderCreatedEvent() {
        MvcTestResult created = createOrder(42L);

        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.totalAmount").isEqualTo(34.0);
        Integer orderId = JsonPath.read(bodyOf(created), "$.id");

        Optional<OrderCreatedEvent> published = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.ORDER_CREATED, OrderCreatedEvent.class, event -> event.getOrderId() == orderId);

        assertThat(published).isPresent();
        OrderCreatedEvent event = published.get();
        assertThat(event.getUserId()).isEqualTo(42L);
        assertThat(event.getTotalAmount()).isEqualByComparingTo("34.00");
        assertThat(event.getItems()).extracting(OrderItemPayload::getItemName).containsExactly("Pizza", "Cola");
    }

    @Test
    void ownerSeesOrderButStrangerDoesNot() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(77L)), "$.id");

        assertThat(mvc.get().uri("/api/orders/" + orderId).header("X-User-Id", "77")).hasStatusOk();
        assertThat(mvc.get().uri("/api/orders/" + orderId).header("X-User-Id", "78")).hasStatus(403);
        assertThat(mvc.get().uri("/api/orders/" + orderId).header("X-User-Id", "78").header("X-User-Role", "ADMIN"))
                .hasStatusOk();
    }

    @Test
    void returnsNotFoundWhenRestaurantDoesNotExist() {
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any()))
                .thenThrow(new EntityNotFoundException("Restaurant with ID: 1 was not found"));

        assertThat(createOrder(42L)).hasStatus(404);
    }

    @Test
    void rejectsOrderWithoutItems() {
        MvcTestResult result = mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"restaurantId": 1, "orderItemsRequest": []}
                        """)
                .exchange();

        assertThat(result).hasStatus(400).bodyJson().extractingPath("$.errors").asMap()
                .containsKey("orderItemsRequest");
    }

    @Test
    void ownerCancelsOwnOrderAndOrderCancelledEventIsPublished() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(88L)), "$.id");

        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel").header("X-User-Id", "89")).hasStatus(403);
        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel").header("X-User-Id", "88")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel").header("X-User-Id", "88")).hasStatus(409);

        Optional<OrderCancelledEvent> published = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.ORDER_CANCELLED, OrderCancelledEvent.class, event -> event.getOrderId() == orderId);

        assertThat(published).isPresent();
        assertThat(published.get().getUserId()).isEqualTo(88L);
        assertThat(published.get().getSequence()).isEqualTo(2L);
    }

    @Test
    void ownerCannotCancelOrderThatIsAlreadyOnItsWay() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(90L)), "$.id");
        patchStatus(orderId, "CONFIRMED");
        patchStatus(orderId, "IN_DELIVERY");

        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel").header("X-User-Id", "90")).hasStatus(409);
        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel")
                .header("X-User-Id", "1").header("X-User-Role", "ADMIN")).hasStatusOk();
    }

    @Test
    void refusesToMoveDeliveredOrderBack() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(42L)), "$.id");

        assertThat(patchStatus(orderId, "DELIVERED")).hasStatus(409);
        assertThat(patchStatus(orderId, "CONFIRMED")).hasStatusOk();
        assertThat(patchStatus(orderId, "IN_DELIVERY")).hasStatusOk();
        assertThat(patchStatus(orderId, "DELIVERED")).hasStatusOk();
        assertThat(patchStatus(orderId, "CONFIRMED")).hasStatus(409);
    }

    @Test
    void rejectsTooBigQuantityAndTooManyLines() {
        MvcTestResult tooMany = mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": 1, \"orderItemsRequest\": [{\"menuItemId\": 10, \"quantity\": 101}]}")
                .exchange();
        String lines = String.join(",", Collections.nCopies(51, "{\"menuItemId\": 10, \"quantity\": 1}"));
        MvcTestResult tooLong = mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": 1, \"orderItemsRequest\": [" + lines + "]}")
                .exchange();

        assertThat(tooMany).hasStatus(400).bodyJson().extractingPath("$.errors").asMap()
                .containsKey("orderItemsRequest[0].quantity");
        assertThat(tooLong).hasStatus(400).bodyJson().extractingPath("$.errors").asMap()
                .containsKey("orderItemsRequest");
    }

    @Test
    void duplicatedLinesBecomeOneOrderItem() {
        MvcTestResult created = mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": 1, \"orderItemsRequest\": ["
                        + "{\"menuItemId\": 10, \"quantity\": 1}, {\"menuItemId\": 10, \"quantity\": 2}]}")
                .exchange();

        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.orderItems").asArray().hasSize(1);
        assertThat(created).bodyJson().extractingPath("$.orderItems[0].quantity").isEqualTo(3);
    }

    private MvcTestResult createOrder(long userId) {
        return mvc.post().uri("/api/orders")
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ORDER_REQUEST)
                .exchange();
    }

    private MvcTestResult patchStatus(Integer orderId, String status) {
        return mvc.patch().uri("/api/orders/" + orderId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}")
                .exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
