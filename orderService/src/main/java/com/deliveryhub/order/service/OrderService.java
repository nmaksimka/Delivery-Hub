package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.exception.OrderRejectedException;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.security.CurrentUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE_TYPE = "Order";
    public static final String ORDERS_PLACED = "orders.placed";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RestaurantCatalog restaurantCatalog;
    private final Outbox outbox;
    private final ProcessedEvents processedEvents;
    private final Clock clock;
    private final Counter ordersCreated;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        RestaurantCatalog restaurantCatalog,
                        Outbox outbox,
                        ProcessedEvents processedEvents,
                        Clock clock,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.restaurantCatalog = restaurantCatalog;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.ordersCreated = Counter.builder(ORDERS_PLACED)
                .description("Orders accepted by the service")
                .register(meterRegistry);
    }

    public OrderDto getOrderById(Long id, CurrentUser currentUser) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));

        if (!currentUser.isAdmin() && !order.belongsTo(currentUser.id())) {
            throw new ForbiddenException("Order " + id + " does not belong to the current user");
        }
        return orderMapper.toDto(order);
    }

    public Page<OrderDto> getOrdersOfUser(Long userId, Pageable pageable) {
        return withItems(orderRepository.findIdsByUserId(userId, pageable));
    }

    public Page<OrderDto> getAllOrders(Pageable pageable) {
        return withItems(orderRepository.findAllIds(pageable));
    }

    @Transactional
    public OrderDto createOrder(CreateOrderRequest createRequest, Long userId) {
        log.info("Creating order for user {} from restaurant {}", userId, createRequest.restaurantId());

        RestaurantDto restaurant = restaurantCatalog.restaurant(createRequest.restaurantId());
        if (!restaurant.active()) {
            throw new OrderRejectedException("Restaurant " + restaurant.id() + " is not active");
        }

        Order order = Order.place(userId, createRequest.restaurantId(), clock.instant());

        for (OrderItemRequest itemRequest : createRequest.orderItemsRequest()) {
            MenuItemDto menuItem = restaurantCatalog.menuItem(createRequest.restaurantId(), itemRequest.menuItemId());

            if (!menuItem.available()) {
                throw new OrderRejectedException("Menu item " + menuItem.id() + " is not available");
            }

            order.addItem(menuItem.id(), menuItem.name(), itemRequest.quantity(), menuItem.price());
        }

        long sequence = order.nextEventSequence();
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id={}, total={}", savedOrder.getId(), savedOrder.getTotalAmount());

        OrderCreatedEvent event = orderCreatedEvent(savedOrder, sequence);
        outbox.append(AGGREGATE_TYPE, savedOrder.getId(), Topics.ORDER_CREATED, event.getEventId(), event);
        ordersCreated.increment();

        return orderMapper.toDto(savedOrder);
    }

    @Transactional
    public OrderDto updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));

        order.changeStatus(newStatus, clock.instant());
        log.info("Order {} status updated to {}", id, newStatus);
        return orderMapper.toDto(order);
    }

    @Transactional
    public void applyDeliveryStatus(DeliveryStatusUpdatedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.DELIVERY_STATUS_UPDATED)) {
            log.info("Event {} is already processed, skipping", event.getEventId());
            return;
        }
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order with ID: " + event.getOrderId() + " was not found"));
        if (!order.acceptDeliveryEvent(event.getSequence())) {
            log.info("Event {} with sequence {} is older than the applied one, dropping",
                    event.getEventId(), event.getSequence());
            return;
        }
        OrderStatus reported = OrderStatusResolver.fromDeliveryStatus(event.getStatus());
        if (order.followDelivery(reported, clock.instant())) {
            log.info("Order {} status updated to {} after delivery event", order.getId(), reported);
        } else {
            log.info("Order {} stays {}, delivery reported {}", order.getId(), order.getStatus(), reported);
        }
    }

    private Page<OrderDto> withItems(Page<Long> ids) {
        Map<Long, Order> ordersById = orderRepository.findWithItemsByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.map(id -> orderMapper.toDto(ordersById.get(id)));
    }

    private OrderCreatedEvent orderCreatedEvent(Order order, long sequence) {
        List<OrderItemPayload> items = order.getOrderItems().stream()
                .map(item -> OrderItemPayload.newBuilder()
                        .setMenuItemId(item.getMenuItemId())
                        .setItemName(item.getItemName())
                        .setQuantity(item.getQuantity())
                        .setPricePerUnit(item.getPricePerUnit())
                        .build())
                .toList();
        return OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setRestaurantId(order.getRestaurantId())
                .setTotalAmount(order.getTotalAmount())
                .setCreatedAt(order.getCreatedAt())
                .setItems(items)
                .setSequence(sequence)
                .build();
    }
}
