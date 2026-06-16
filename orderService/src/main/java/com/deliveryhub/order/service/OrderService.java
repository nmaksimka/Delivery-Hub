package com.deliveryhub.order.service;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.client.RestaurantServiceClient;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderItem;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.event.OrderCreatedDomainEvent;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.repository.OrderRepository;
import com.deliveryhub.order.security.CurrentUser;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RestaurantServiceClient restaurantServiceClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderDto getOrderById(Long id, CurrentUser currentUser) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));

        if (!currentUser.isAdmin() && !order.getUserId().equals(currentUser.id())) {
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
        log.info("Creating order for user {} from restaurant {}", userId, createRequest.getRestaurantId());

        RestaurantDto restaurant = fetchRestaurant(createRequest.getRestaurantId());
        if (!restaurant.isActive()) {
            throw new IllegalStateException("Restaurant " + restaurant.getId() + " is not active");
        }

        Order order = Order.builder()
                .userId(userId)
                .restaurantId(createRequest.getRestaurantId())
                .status(OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : createRequest.getOrderItemsRequest()) {
            MenuItemDto menuItem = fetchMenuItem(createRequest.getRestaurantId(), itemRequest.getMenuItemId());

            if (!menuItem.isAvailable()) {
                throw new IllegalStateException("Menu item " + menuItem.getId() + " is not available");
            }

            order.addOrderItem(OrderItem.builder()
                    .menuItemId(menuItem.getId())
                    .itemName(menuItem.getName())
                    .quantity(itemRequest.getQuantity())
                    .pricePerUnit(menuItem.getPrice())
                    .build());

            total = total.add(menuItem.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id={}, total={}", savedOrder.getId(), total);

        OrderCreatedEvent event = orderMapper.toOrderCreatedEvent(savedOrder);
        applicationEventPublisher.publishEvent(new OrderCreatedDomainEvent(event));

        return orderMapper.toDto(savedOrder);
    }

    @Transactional
    public OrderDto updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));

        order.setStatus(newStatus);
        log.info("Order {} status updated to {}", id, newStatus);
        return orderMapper.toDto(order);
    }

    @Transactional
    public void applyDeliveryStatus(Long orderId, DeliveryStatus deliveryStatus) {
        updateOrderStatus(orderId, OrderStatusResolver.fromDeliveryStatus(deliveryStatus));
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
