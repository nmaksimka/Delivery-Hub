package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.client.RestaurantServiceClient;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.security.CurrentUser;
import feign.Request;
import feign.RequestTemplate;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    private static final long USER_ID = 42L;
    private static final long RESTAURANT_ID = 1L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RestaurantServiceClient restaurantServiceClient;
    @Mock
    private Outbox outbox;
    @Mock
    private ProcessedEvents processedEvents;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateOrderRequest(RESTAURANT_ID, List.of(
                new OrderItemRequest(10L, 2),
                new OrderItemRequest(11L, 3)));
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("считает сумму как сумму цена * количество по всем позициям")
        void calculatesTotalAmount() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", true);
            givenMenuItem(11L, "Cola", "3.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any(Order.class))).thenReturn(dto());

            orderService.createOrder(request, USER_ID);

            verify(orderRepository).save(orderCaptor.capture());
            Order saved = orderCaptor.getValue();

            assertThat(saved.getTotalAmount()).isEqualByComparingTo("34.00");
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(saved.getOrderItems()).hasSize(2);
        }

        @Test
        @DisplayName("берёт userId из аргумента, а не из тела запроса")
        void usesUserIdFromCaller() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "10.00", true);
            givenMenuItem(11L, "Cola", "5.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any(Order.class))).thenReturn(dto());

            orderService.createOrder(request, 777L);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getUserId()).isEqualTo(777L);
        }

        @Test
        @DisplayName("кладёт событие order-created в outbox вместе с заказом")
        void appendsOrderCreatedEventToOutbox() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "10.00", true);
            givenMenuItem(11L, "Cola", "5.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any(Order.class))).thenReturn(dto());

            orderService.createOrder(request, USER_ID);

            ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(outbox).append(eq("Order"), any(), eq(Topics.ORDER_CREATED), captor.capture());
            OrderCreatedEvent event = captor.getValue();
            assertThat(event.eventId()).isNotNull();
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.totalAmount()).isEqualByComparingTo("35.00");
            assertThat(event.items()).extracting(OrderCreatedEvent.OrderItemPayload::itemName)
                    .containsExactly("Pizza", "Cola");
        }

        @Test
        @DisplayName("бросает EntityNotFoundException, если ресторана нет")
        void failsWhenRestaurantMissing() {
            when(restaurantServiceClient.getRestaurantById(RESTAURANT_ID)).thenThrow(notFound());

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Restaurant with ID: 1");

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("бросает IllegalStateException, если ресторан неактивен")
        void failsWhenRestaurantInactive() {
            RestaurantDto restaurant = new RestaurantDto(RESTAURANT_ID, "Pizzeria", false);
            when(restaurantServiceClient.getRestaurantById(RESTAURANT_ID)).thenReturn(restaurant);

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not active");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("бросает IllegalStateException, если блюдо недоступно")
        void failsWhenMenuItemUnavailable() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", false);

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not available");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("бросает EntityNotFoundException, если блюда нет в этом ресторане")
        void failsWhenMenuItemMissing() {
            givenActiveRestaurant();
            when(restaurantServiceClient.getMenuItemById(RESTAURANT_ID, 10L)).thenThrow(notFound());

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Menu item with ID: 10");
        }
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("отдаёт заказ владельцу")
        void returnsOwnOrder() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
            OrderDto dto = dto();
            when(orderMapper.toDto(order)).thenReturn(dto);

            assertThat(orderService.getOrderById(1L, new CurrentUser(USER_ID, "USER"))).isSameAs(dto);
        }

        @Test
        @DisplayName("запрещает читать чужой заказ")
        void forbidsForeignOrder() {
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(orderOf(USER_ID)));

            assertThatThrownBy(() -> orderService.getOrderById(1L, new CurrentUser(999L, "USER")))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("разрешает ADMIN читать любой заказ")
        void allowsAdmin() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
            when(orderMapper.toDto(order)).thenReturn(dto());

            assertThat(orderService.getOrderById(1L, new CurrentUser(999L, "ADMIN"))).isNotNull();
        }

        @Test
        @DisplayName("бросает EntityNotFoundException для несуществующего заказа")
        void failsWhenMissing() {
            when(orderRepository.findWithItemsById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(404L, new CurrentUser(USER_ID, "USER")))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("applyDeliveryStatus")
    class ApplyDeliveryStatus {

        @Test
        @DisplayName("переводит заказ в статус заказа, а не доставки")
        void mapsDeliveryStatusToOrderStatus() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
            when(orderMapper.toDto(order)).thenReturn(dto());

            DeliveryStatusUpdatedEvent event = statusUpdate(DeliveryStatus.IN_TRANSIT);
            when(processedEvents.registerFirstDelivery(event.eventId(), Topics.DELIVERY_STATUS_UPDATED))
                    .thenReturn(true);

            orderService.applyDeliveryStatus(event);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
        }

        @Test
        @DisplayName("повторно доставленное событие не трогает заказ")
        void skipsAlreadyProcessedEvent() {
            DeliveryStatusUpdatedEvent event = statusUpdate(DeliveryStatus.DELIVERED);
            when(processedEvents.registerFirstDelivery(event.eventId(), Topics.DELIVERY_STATUS_UPDATED))
                    .thenReturn(false);

            orderService.applyDeliveryStatus(event);

            verifyNoInteractions(orderRepository);
        }

        private DeliveryStatusUpdatedEvent statusUpdate(DeliveryStatus status) {
            return new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 7L, 1L, status, Instant.EPOCH);
        }
    }


    private void givenActiveRestaurant() {
        RestaurantDto restaurant = new RestaurantDto(RESTAURANT_ID, "Pizzeria", true);
        when(restaurantServiceClient.getRestaurantById(RESTAURANT_ID)).thenReturn(restaurant);
    }

    private void givenMenuItem(long id, String name, String price, boolean available) {
        MenuItemDto item = new MenuItemDto(id, name, new BigDecimal(price), available);
        when(restaurantServiceClient.getMenuItemById(RESTAURANT_ID, id)).thenReturn(item);
    }

    private static Order orderOf(long userId) {
        Order order = Order.place(userId, RESTAURANT_ID);
        order.addItem(10L, "Pizza", 1, BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    private static OrderDto dto() {
        return new OrderDto(1L, USER_ID, RESTAURANT_ID, OrderStatus.CREATED, BigDecimal.TEN,
                Instant.EPOCH, Instant.EPOCH, List.of());
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/restaurants/1",
                Map.of(), null, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, Collections.emptyMap());
    }
}
