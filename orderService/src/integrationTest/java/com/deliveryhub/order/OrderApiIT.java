package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.order.client.RestaurantServiceClient;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private RestaurantServiceClient restaurantServiceClient;

    @BeforeEach
    void stubRestaurantService() {
        when(restaurantServiceClient.getRestaurantById(RESTAURANT_ID))
                .thenReturn(new RestaurantDto(RESTAURANT_ID, "Pizzeria", true));
        when(restaurantServiceClient.getMenuItemById(RESTAURANT_ID, 10L))
                .thenReturn(new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true));
        when(restaurantServiceClient.getMenuItemById(RESTAURANT_ID, 11L))
                .thenReturn(new MenuItemDto(11L, "Cola", new BigDecimal("3.00"), true));
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
        when(restaurantServiceClient.getRestaurantById(RESTAURANT_ID)).thenThrow(notFound());

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
    void refusesToMoveDeliveredOrderBack() {
        Integer orderId = JsonPath.read(bodyOf(createOrder(42L)), "$.id");

        assertThat(patchStatus(orderId, "DELIVERED")).hasStatusOk();
        assertThat(patchStatus(orderId, "CONFIRMED")).hasStatus(409);
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

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/restaurants/1",
                Map.of(), null, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, Map.of());
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
