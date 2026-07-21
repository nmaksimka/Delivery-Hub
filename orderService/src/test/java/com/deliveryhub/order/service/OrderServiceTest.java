package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.exception.InvalidStatusTransitionException;
import com.deliveryhub.order.exception.OrderRejectedException;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.security.CurrentUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RestaurantCatalog restaurantCatalog;
    @Mock
    private Outbox outbox;
    @Mock
    private ProcessedEvents processedEvents;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final List<MenuItemDto> menu = new ArrayList<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderMapper,
                restaurantCatalog, outbox,
                processedEvents, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager, meterRegistry);
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
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);
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
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);
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
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);
            when(orderMapper.toDto(any(Order.class))).thenReturn(dto());

            orderService.createOrder(request, USER_ID);

            ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(outbox).append(eq("Order"), any(), eq(Topics.ORDER_CREATED), any(), captor.capture());
            OrderCreatedEvent event = captor.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getUserId()).isEqualTo(USER_ID);
            assertThat(event.getTotalAmount()).isEqualByComparingTo("35.00");
            assertThat(event.getItems()).extracting(OrderItemPayload::getItemName)
                    .containsExactly("Pizza", "Cola");
        }

        @Test
        @DisplayName("считает созданные заказы в метрике orders.placed")
        void countsCreatedOrders() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "10.00", true);
            givenMenuItem(11L, "Cola", "5.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);
            when(orderMapper.toDto(any(Order.class))).thenReturn(dto());

            orderService.createOrder(request, USER_ID);
            orderService.createOrder(request, USER_ID);

            assertThat(meterRegistry.get(OrderService.ORDERS_PLACED).counter().count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("не считает заказ, который не удалось создать")
        void doesNotCountRejectedOrder() {
            givenRestaurant(false);

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(OrderRejectedException.class);

            assertThat(meterRegistry.get(OrderService.ORDERS_PLACED).counter().count()).isZero();
        }

        @Test
        @DisplayName("одинаковые позиции схлопываются в одну строку с суммой количеств")
        void mergesDuplicatedMenuItems() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);

            orderService.createOrder(new CreateOrderRequest(RESTAURANT_ID, List.of(
                    new OrderItemRequest(10L, 2), new OrderItemRequest(10L, 3))), USER_ID);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderItems()).singleElement()
                    .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(5));
            assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("62.50");
            verify(restaurantCatalog).quote(RESTAURANT_ID, Set.of(10L));
        }

        @Test
        @DisplayName("сумма количеств одной позиции не может обойти лимит")
        void rejectsMergedQuantityAboveLimit() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", true);

            assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(RESTAURANT_ID, List.of(
                    new OrderItemRequest(10L, 60), new OrderItemRequest(10L, 60))), USER_ID))
                    .isInstanceOf(OrderRejectedException.class)
                    .hasMessageContaining("exceeds");
            verifyNoInteractions(transactionManager);
        }

        @Test
        @DisplayName("каталог опрашивается до открытия транзакции")
        void asksCatalogBeforeOpeningTransaction() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", true);
            givenMenuItem(11L, "Cola", "3.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);

            orderService.createOrder(request, USER_ID);

            InOrder order = inOrder(restaurantCatalog, transactionManager, orderRepository);
            order.verify(restaurantCatalog).quote(eq(RESTAURANT_ID), any());
            order.verify(transactionManager).getTransaction(any());
            order.verify(orderRepository).save(any(Order.class));
            order.verify(transactionManager).commit(any());
        }

        @Test
        @DisplayName("счётчик не растёт, если транзакция не закоммитилась")
        void doesNotCountOrderWhenCommitFails() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", true);
            givenMenuItem(11L, "Cola", "3.00", true);
            when(orderRepository.save(any(Order.class))).thenAnswer(OrderServiceTest::savedWithId);
            doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(TransactionSystemException.class);

            assertThat(meterRegistry.get(OrderService.ORDERS_PLACED).counter().count()).isZero();
        }

        @Test
        @DisplayName("бросает EntityNotFoundException, если ресторана нет")
        void failsWhenRestaurantMissing() {
            when(restaurantCatalog.quote(eq(RESTAURANT_ID), any()))
                    .thenThrow(new EntityNotFoundException("Restaurant with ID: 1 was not found"));

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Restaurant with ID: 1");

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("отклоняет заказ, если ресторан неактивен")
        void failsWhenRestaurantInactive() {
            givenRestaurant(false);

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(OrderRejectedException.class)
                    .hasMessageContaining("is not active");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("отклоняет заказ в закрытый ресторан и называет часы работы")
        void failsWhenRestaurantClosed() {
            givenRestaurant(new RestaurantDto(RESTAURANT_ID, "Pizzeria", true, true, "10:00-22:00 Asia/Tomsk"));

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(OrderRejectedException.class)
                    .hasMessage("Restaurant 1 is closed now, working hours: 10:00-22:00 Asia/Tomsk");

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("закрытый ресторан без расписания отклоняется без часов в сообщении")
        void failsWhenRestaurantClosedWithoutHours() {
            givenRestaurant(new RestaurantDto(RESTAURANT_ID, "Pizzeria", true, true, ""));

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(OrderRejectedException.class)
                    .hasMessage("Restaurant 1 is closed now");
        }

        @Test
        @DisplayName("отклоняет заказ, если блюдо недоступно")
        void failsWhenMenuItemUnavailable() {
            givenActiveRestaurant();
            givenMenuItem(10L, "Pizza", "12.50", false);

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                    .isInstanceOf(OrderRejectedException.class)
                    .hasMessageContaining("is not available");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("бросает EntityNotFoundException, если блюда нет в этом ресторане")
        void failsWhenMenuItemMissing() {
            givenActiveRestaurant();

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
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("владелец отменяет заказ, событие OrderCancelled уходит в outbox со следующим номером")
        void ownerCancelsAndEventIsAppended() {
            Order order = orderOf(USER_ID);
            order.nextEventSequence();
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.cancelOrder(1L, new CurrentUser(USER_ID, "USER"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            ArgumentCaptor<OrderCancelledEvent> event = ArgumentCaptor.forClass(OrderCancelledEvent.class);
            verify(outbox).append(eq("Order"), eq(1L), eq(Topics.ORDER_CANCELLED), any(), event.capture());
            assertThat(event.getValue().getOrderId()).isEqualTo(1L);
            assertThat(event.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(event.getValue().getSequence()).isEqualTo(2L);
            assertThat(event.getValue().getCancelledAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("чужой заказ отменить нельзя")
        void strangerCannotCancel() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, new CurrentUser(USER_ID + 1, "USER")))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("заказ в пути владелец отменить уже не может, а администратор может")
        void onlyAdminCancelsOrderInDelivery() {
            Order order = orderOf(USER_ID);
            ReflectionTestUtils.setField(order, "status", OrderStatus.IN_DELIVERY);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, new CurrentUser(USER_ID, "USER")))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            verifyNoInteractions(outbox);

            orderService.cancelOrder(1L, new CurrentUser(1L, "ADMIN"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(outbox).append(eq("Order"), eq(1L), eq(Topics.ORDER_CANCELLED), any(), any());
        }

        @Test
        @DisplayName("повторная отмена администратором не порождает второго события")
        void repeatedAdminCancelAppendsNothing() {
            Order order = orderOf(USER_ID);
            ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.cancelOrder(1L, new CurrentUser(1L, "ADMIN"));

            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("смена статуса на CANCELLED через PATCH тоже публикует OrderCancelled")
        void statusPatchToCancelledAppendsEvent() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.updateOrderStatus(1L, OrderStatus.CANCELLED);
            orderService.updateOrderStatus(1L, OrderStatus.CANCELLED);

            verify(outbox).append(eq("Order"), eq(1L), eq(Topics.ORDER_CANCELLED), any(), any());
        }

        @Test
        @DisplayName("обычная смена статуса событий не публикует")
        void plainStatusChangeAppendsNothing() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

            orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);

            verifyNoInteractions(outbox);
        }
    }

    @Nested
    @DisplayName("applyDeliveryStatus")
    class ApplyDeliveryStatus {

        @Test
        @DisplayName("переводит заказ в статус заказа, а не доставки")
        void mapsDeliveryStatusToOrderStatus() {
            Order order = orderOf(USER_ID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            DeliveryStatusUpdatedEvent event = statusUpdate(DeliveryStatus.IN_TRANSIT);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.DELIVERY_STATUS_UPDATED))
                    .thenReturn(true);

            orderService.applyDeliveryStatus(event);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
        }

        @Test
        @DisplayName("событие старее уже применённого отбрасывается")
        void dropsStaleEvent() {
            Order order = orderOf(USER_ID);
            order.acceptDeliveryEvent(5L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            DeliveryStatusUpdatedEvent stale = statusUpdate(DeliveryStatus.DELIVERED);
            when(processedEvents.registerFirstDelivery(stale.getEventId(), Topics.DELIVERY_STATUS_UPDATED))
                    .thenReturn(true);

            orderService.applyDeliveryStatus(stale);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        }

        @Test
        @DisplayName("повторно доставленное событие не трогает заказ")
        void skipsAlreadyProcessedEvent() {
            DeliveryStatusUpdatedEvent event = statusUpdate(DeliveryStatus.DELIVERED);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.DELIVERY_STATUS_UPDATED))
                    .thenReturn(false);

            orderService.applyDeliveryStatus(event);

            verifyNoInteractions(orderRepository);
        }

        private DeliveryStatusUpdatedEvent statusUpdate(DeliveryStatus status) {
            return new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 7L, 1L, status, Instant.EPOCH, 2L);
        }
    }


    private void givenActiveRestaurant() {
        givenRestaurant(true);
    }

    private void givenRestaurant(boolean active) {
        givenRestaurant(new RestaurantDto(RESTAURANT_ID, "Pizzeria", active));
    }

    private void givenRestaurant(RestaurantDto restaurant) {
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any())).thenAnswer(invocation -> new MenuQuote(restaurant,
                menu.stream().collect(Collectors.toMap(MenuItemDto::id, Function.identity()))));
    }

    private void givenMenuItem(long id, String name, String price, boolean available) {
        menu.add(new MenuItemDto(id, name, new BigDecimal(price), available));
    }

    private static Order orderOf(long userId) {
        Order order = Order.place(userId, RESTAURANT_ID, NOW);
        order.addItem(10L, "Pizza", 1, BigDecimal.TEN);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    private static OrderDto dto() {
        return new OrderDto(1L, USER_ID, RESTAURANT_ID, OrderStatus.CREATED, BigDecimal.TEN,
                Instant.EPOCH, Instant.EPOCH, List.of());
    }

    private static Order savedWithId(InvocationOnMock invocation) {
        Order order = invocation.getArgument(0);
        ReflectionTestUtils.setField(order, "id", 15L);
        return order;
    }
}
