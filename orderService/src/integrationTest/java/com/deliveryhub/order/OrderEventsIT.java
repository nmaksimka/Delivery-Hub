package com.deliveryhub.order;

import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.DeliveryQuotes;
import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.outbox.OutboxEvent;
import com.deliveryhub.order.outbox.OutboxEventRepository;
import com.deliveryhub.order.outbox.OutboxMetrics;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Autowired
    private MeterRegistry meterRegistry;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;
    @MockitoBean
    private DeliveryQuotes deliveryQuotes;

    @BeforeEach
    void stubRestaurantService() {
        when(restaurantCatalog.quote(eq(1L), any())).thenReturn(new MenuQuote(
                new RestaurantDto(1L, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true))));
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
        Long orderId = placeConfirmedOrder();
        UUID eventId = UUID.randomUUID();
        DeliveryStatusUpdatedEvent picked =
                new DeliveryStatusUpdatedEvent(eventId, 1L, orderId, DeliveryStatus.PICKED_UP, Instant.now(), 2L);
        DeliveryStatusUpdatedEvent sameIdOtherStatus =
                new DeliveryStatusUpdatedEvent(eventId, 1L, orderId, DeliveryStatus.DELIVERED, Instant.now(), 4L);

        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), picked);
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), sameIdOtherStatus);
        Long markerOrderId = placeOrder();
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, markerOrderId.toString(), new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 2L, markerOrderId, DeliveryStatus.CANCELLED, Instant.now(), 2L));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(markerOrderId)).isEqualTo(OrderStatus.CANCELLED));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.IN_DELIVERY);
    }

    @Test
    void dropsDeliveryEventThatIsOlderThanAppliedOne() {
        Long orderId = placeConfirmedOrder();
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 1L, orderId, DeliveryStatus.PICKED_UP, Instant.now(), 2L));
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, orderId.toString(), new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 1L, orderId, DeliveryStatus.CANCELLED, Instant.now(), 1L));
        Long markerOrderId = placeOrder();
        kafkaTemplate.send(Topics.DELIVERY_STATUS_UPDATED, markerOrderId.toString(), new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 2L, markerOrderId, DeliveryStatus.CANCELLED, Instant.now(), 1L));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(statusOf(markerOrderId)).isEqualTo(OrderStatus.CANCELLED));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.IN_DELIVERY);
    }

    @Test
    void exposesBusinessAndOutboxMetrics() {
        double before = meterRegistry.get(OrderService.ORDERS_PLACED).counter().count();

        Long orderId = placeOrder();

        assertThat(meterRegistry.get(OrderService.ORDERS_PLACED).counter().count()).isEqualTo(before + 1);
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(meterRegistry.get(OutboxMetrics.PENDING).gauge().value()).isZero());
        assertThat(orderRepository.existsById(orderId)).isTrue();
    }

    private Long placeOrder() {
        UserAddressDto address = new UserAddressDto(77L, "Томск", "Ленина", "12", null, null, null, null,
                new BigDecimal("56.47"), new BigDecimal("84.97"), true);
        when(deliveryQuotes.quote(any(), any(), any(UserAddressDto.class))).thenReturn(new DeliveryQuoteDto(1500, 20,
                new BigDecimal("0.00"), "OSRM", new DeliveryQuoteDto.Weather("CLEAR", 5.0, new BigDecimal("0.00"), 0)));
        return orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(10L, 2))), 42L, null, address).id();
    }

    private Long placeConfirmedOrder() {
        Order order = orderRepository.findById(placeOrder()).orElseThrow();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        return orderRepository.save(order).getId();
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }
}
