package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.client.RestaurantServiceClient;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.outbox.OutboxEvent;
import com.deliveryhub.order.outbox.OutboxEventRepository;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class OrderEventsIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private KafkaContainer kafka;
    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;

    @BeforeEach
    void stubRestaurantService() {
        when(restaurantServiceClient.getRestaurantById(1L)).thenReturn(new RestaurantDto(1L, "Pizzeria", true));
        when(restaurantServiceClient.getMenuItemById(1L, 10L))
                .thenReturn(new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true));
    }

    @Test
    void relayPublishesOutboxEventWithItsIdAndMarksItPublished() {
        Long orderId = placeOrder();

        OutboxEvent stored = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId.toString()))
                .findFirst()
                .orElseThrow();
        assertThat(stored.getEventType()).isEqualTo(Topics.ORDER_CREATED);
        assertThat(stored.getAggregateType()).isEqualTo("Order");

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(outboxEventRepository.findById(stored.getId()).orElseThrow().isPublished()).isTrue());

        Optional<OrderCreatedEvent> published = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.ORDER_CREATED, OrderCreatedEvent.class, event -> event.getOrderId() == orderId);

        assertThat(published).isPresent();
        assertThat(published.get().getEventId()).isEqualTo(stored.getId());
        assertThat(published.get().getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void appliesDeliveryStatusOnceEvenIfEventArrivesTwice() {
        Long orderId = placeOrder();
        UUID eventId = UUID.randomUUID();
        DeliveryStatusUpdatedEvent picked =
                new DeliveryStatusUpdatedEvent(eventId, 1L, orderId, DeliveryStatus.PICKED_UP, Instant.now());
        DeliveryStatusUpdatedEvent sameIdOtherStatus =
                new DeliveryStatusUpdatedEvent(eventId, 1L, orderId, DeliveryStatus.DELIVERED, Instant.now());

        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), picked);
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), sameIdOtherStatus);
        Long markerOrderId = placeOrder();
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, markerOrderId.toString(), new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 2L, markerOrderId, DeliveryStatus.CANCELLED, Instant.now()));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(markerOrderId)).isEqualTo(OrderStatus.CANCELLED));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.IN_DELIVERY);
    }

    private Long placeOrder() {
        return orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(10L, 2))), 42L).id();
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }
}
