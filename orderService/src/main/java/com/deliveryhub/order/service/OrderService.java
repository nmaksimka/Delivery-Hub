package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
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
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE_TYPE = "Order";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RestaurantServiceClient restaurantServiceClient;
    private final Outbox outbox;
    private final ProcessedEvents processedEvents;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        RestaurantServiceClient restaurantServiceClient,
                        Outbox outbox,
                        ProcessedEvents processedEvents) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.restaurantServiceClient = restaurantServiceClient;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
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
        return orderRepository.findByUserId(userId, pageable).map(orderMapper::toDto);
    }

    public Page<OrderDto> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toDto);
    }

    @Transactional
    public OrderDto createOrder(CreateOrderRequest createRequest, Long userId) {
        log.info("Creating order for user {} from restaurant {}", userId, createRequest.restaurantId());

        RestaurantDto restaurant = fetchRestaurant(createRequest.restaurantId());
        if (!restaurant.active()) {
            throw new IllegalStateException("Restaurant " + restaurant.id() + " is not active");
        }

        Order order = Order.place(userId, createRequest.restaurantId());

        for (OrderItemRequest itemRequest : createRequest.orderItemsRequest()) {
            MenuItemDto menuItem = fetchMenuItem(createRequest.restaurantId(), itemRequest.menuItemId());

            if (!menuItem.available()) {
                throw new IllegalStateException("Menu item " + menuItem.id() + " is not available");
            }

            order.addItem(menuItem.id(), menuItem.name(), itemRequest.quantity(), menuItem.price());
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id={}, total={}", savedOrder.getId(), savedOrder.getTotalAmount());

        outbox.append(AGGREGATE_TYPE, savedOrder.getId(), Topics.ORDER_CREATED, orderCreatedEvent(savedOrder));

        return orderMapper.toDto(savedOrder);
    }

    @Transactional
    public OrderDto updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));

        order.applyStatus(newStatus);
        log.info("Order {} status updated to {}", id, newStatus);
        return orderMapper.toDto(order);
    }

    @Transactional
    public void applyDeliveryStatus(DeliveryStatusUpdatedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.eventId(), Topics.DELIVERY_STATUS_UPDATED)) {
            log.info("Event {} is already processed, skipping", event.eventId());
            return;
        }
        updateOrderStatus(event.orderId(), OrderStatusResolver.fromDeliveryStatus(event.status()));
    }

    private OrderCreatedEvent orderCreatedEvent(Order order) {
        List<OrderCreatedEvent.OrderItemPayload> items = order.getOrderItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItemPayload(
                        item.getMenuItemId(), item.getItemName(), item.getQuantity(), item.getPricePerUnit()))
                .toList();
        return new OrderCreatedEvent(UUID.randomUUID(), order.getId(), order.getUserId(), order.getRestaurantId(),
                order.getTotalAmount(), order.getCreatedAt(), items);
    }

    private RestaurantDto fetchRestaurant(Long restaurantId) {
        try {
            return restaurantServiceClient.getRestaurantById(restaurantId);
        } catch (FeignException.NotFound err) {
            throw new EntityNotFoundException("Restaurant with ID: " + restaurantId + " was not found");
        }
    }

    private MenuItemDto fetchMenuItem(Long restaurantId, Long menuItemId) {
        try {
            return restaurantServiceClient.getMenuItemById(restaurantId, menuItemId);
        } catch (FeignException.NotFound err) {
            throw new EntityNotFoundException(
                    "Menu item with ID: " + menuItemId + " was not found in restaurant " + restaurantId);
        }
    }
}
