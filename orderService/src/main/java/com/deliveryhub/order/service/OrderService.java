package com.deliveryhub.order.service;

import com.deliveryhub.order.client.RestaurantServiceClient;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderItem;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.repository.OrderRepository;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RestaurantServiceClient restaurantServiceClient;

    public OrderDto getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));
        return orderMapper.toDto(order);
    }

    public List<OrderDto> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(orderMapper::toDto).collect(Collectors.toList());
    }

    public List<OrderDto> getOrders() {
        return orderRepository.findAll().stream().map(orderMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public OrderDto createOrder(CreateOrderRequest createRequest) {
        log.info("Creating order for user {} from restaurant {}", createRequest.getUserId(), createRequest.getRestaurantId());

        RestaurantDto restaurantDto;
        try {
            restaurantDto = restaurantServiceClient.getRestaurantById(createRequest.getRestaurantId());
        } catch (FeignException.NotFound err) {
            throw new EntityNotFoundException("Restaurant with ID: " + createRequest.getRestaurantId() + " was not found");
        }

        if (!restaurantDto.isActive()) {
            throw new IllegalStateException("Restaurant is not active");
        }

        Order order = Order.builder()
                .userId(createRequest.getUserId())
                .restaurantId(createRequest.getRestaurantId())
                .status("CREATED")
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for(OrderItemRequest orderItemRequest : createRequest.getOrderItemsRequest()) {
            MenuItemDto menuItem;
            try {
                menuItem = restaurantServiceClient
                        .getMenuItemById(createRequest.getRestaurantId(), orderItemRequest.getMenuItemId());
            } catch (FeignException.NotFound err) {
                throw new EntityNotFoundException("Menu Item with ID: " + orderItemRequest.getMenuItemId() + " was not found");
            }

            if(!menuItem.isAvailable()) throw new IllegalStateException("Menu item '" + menuItem.getName() + "' is not available");

            OrderItem orderItem = OrderItem.builder()
                    .menuItemId(menuItem.getId())
                    .itemName(menuItem.getName())
                    .quantity(orderItemRequest.getQuantity())
                    .pricePerUnit(menuItem.getPrice())
                    .build();
            order.addOrderItem(orderItem);

            total = total.add(menuItem.getPrice().multiply(BigDecimal.valueOf(orderItemRequest.getQuantity())));
        }

        order.setTotalAmount(total);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id: {}", savedOrder.getId());

        return orderMapper.toDto(savedOrder);
    }

    @Transactional
    public OrderDto updateOrderStatus(Long id, String newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));
        order.setStatus(newStatus);
        log.info("Order {} status updated to {}", id, newStatus);
        //pozhe dobavit' kafky
        return orderMapper.toDto(order);
    }
}
