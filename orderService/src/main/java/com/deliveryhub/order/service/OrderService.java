package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.GeoPoint;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.order.client.DeliveryQuotes;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.dto.GeoLocation;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.DeliveryDestination;
import com.deliveryhub.order.entity.DeliveryFeeBreakdown;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.exception.ItemNotFoundException;
import com.deliveryhub.order.exception.OrderRejectedException;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.mapper.OrderMapper;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.promo.PromoService;
import com.deliveryhub.order.promo.PromoUse;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final PromoService promoService;
    private final OrderCancellation cancellation;
    private final DeliveryQuotes deliveryQuotes;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Counter ordersPlaced;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        RestaurantCatalog restaurantCatalog,
                        Outbox outbox,
                        ProcessedEvents processedEvents,
                        PromoService promoService,
                        OrderCancellation cancellation,
                        DeliveryQuotes deliveryQuotes,
                        Clock clock,
                        PlatformTransactionManager transactionManager,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.restaurantCatalog = restaurantCatalog;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.promoService = promoService;
        this.cancellation = cancellation;
        this.deliveryQuotes = deliveryQuotes;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.ordersPlaced = Counter.builder(ORDERS_PLACED)
                .description("Orders accepted by the service")
                .register(meterRegistry);
    }

    public OrderDto getOrderById(Long id, CurrentUser currentUser) {
        Order order = findWithItemsOrThrow(id);

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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderDto createOrder(CreateOrderRequest createRequest, Long userId, String promoCode,
                                UserAddressDto address) {
        Objects.requireNonNull(address, "Delivery address is required");
        Long restaurantId = createRequest.restaurantId();
        log.info("Creating order for user {} from restaurant {}", userId, restaurantId);

        Map<Long, Integer> quantities = createRequest.quantitiesByMenuItem();
        MenuQuote quote = restaurantCatalog.quote(restaurantId, quantities.keySet());
        if (!quote.restaurant().active()) {
            throw new OrderRejectedException(ErrorCode.RESTAURANT_INACTIVE,
                    "Restaurant " + restaurantId + " is not active");
        }
        if (quote.restaurant().closed()) {
            throw new OrderRejectedException(ErrorCode.RESTAURANT_CLOSED, closedMessage(quote.restaurant()));
        }

        Order order = Order.place(userId, restaurantId, clock.instant());
        quantities.forEach((menuItemId, quantity) -> {
            MenuItemDto menuItem = quote.item(menuItemId).orElseThrow(() -> new ItemNotFoundException(ErrorCode.ITEM_NOT_FOUND,
                    "Menu item with ID: " + menuItemId + " was not found in restaurant " + restaurantId));
            if (!menuItem.available()) {
                throw new OrderRejectedException(ErrorCode.ITEM_UNAVAILABLE,
                        "Menu item " + menuItemId + " is not available");
            }
            if (quantity > OrderItemRequest.MAX_QUANTITY) {
                throw new OrderRejectedException(ErrorCode.QUANTITY_LIMIT, "Quantity of menu item " + menuItemId
                        + " exceeds " + OrderItemRequest.MAX_QUANTITY);
            }
            order.addItem(menuItem.id(), menuItem.name(), quantity, menuItem.price());
        });
        DeliveryQuoteDto delivery = deliveryQuotes.quote(restaurantId, quote.restaurant().location(), address);
        order.deliverTo(new DeliveryDestination(address.id(), address.line(), address.courierComment(),
                address.latitude(), address.longitude()), delivery.deliveryFee(), delivery.etaMinutes(),
                new DeliveryFeeBreakdown(delivery.distanceMeters(), weatherSurcharge(delivery), delivery.routeSource()));

        Order savedOrder = transaction.execute(status -> store(order, promoCode, quote.restaurant().location()));
        ordersPlaced.increment();
        log.info("Order created with id={}, total={}", savedOrder.getId(), savedOrder.getTotalAmount());
        return orderMapper.toDto(savedOrder);
    }

    private static BigDecimal weatherSurcharge(DeliveryQuoteDto delivery) {
        return delivery.weather() == null || delivery.weather().surcharge() == null
                ? BigDecimal.ZERO
                : delivery.weather().surcharge();
    }

    private static String closedMessage(RestaurantDto restaurant) {
        String closed = "Restaurant " + restaurant.id() + " is closed now";
        return restaurant.workingHours().isBlank()
                ? closed
                : closed + ", working hours: " + restaurant.workingHours();
    }

    private Order store(Order order, String promoCode, GeoLocation restaurantLocation) {
        PromoUse promo = null;
        if (promoCode != null && !promoCode.isBlank()) {
            promo = promoService.reserve(promoCode, order.getUserId(), order.getRestaurantId(), order.subtotal());
            order.applyDiscount(promo.code(), promo.discount());
        }
        long sequence = order.nextEventSequence();
        Order savedOrder = orderRepository.save(order);
        if (promo != null) {
            promoService.record(promo, savedOrder.getUserId(), savedOrder.getId());
        }
        OrderCreatedEvent event = orderCreatedEvent(savedOrder, sequence, restaurantLocation);
        outbox.append(AGGREGATE_TYPE, savedOrder.getId(), Topics.ORDER_CREATED, event.getEventId(), event);
        return savedOrder;
    }

    @Transactional
    public OrderDto updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = findWithItemsOrThrow(id);

        if (newStatus == OrderStatus.CANCELLED) {
            cancellation.cancel(order, CancellationReason.ADMIN);
        } else if (order.changeStatus(newStatus, clock.instant())) {
            log.info("Order {} status updated to {}", id, newStatus);
        }
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto cancelOrder(Long id, CurrentUser currentUser) {
        Order order = findWithItemsOrThrow(id);

        if (currentUser.isAdmin()) {
            cancellation.cancel(order, CancellationReason.ADMIN);
            return orderMapper.toDto(order);
        }
        if (!order.belongsTo(currentUser.id())) {
            throw new ForbiddenException("Order " + id + " does not belong to the current user");
        }
        cancellation.cancel(order, CancellationReason.CUSTOMER);
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
        if (reported == OrderStatus.CANCELLED) {
            if (!order.getStatus().isFinal()) {
                cancellation.cancel(order, CancellationReason.DELIVERY_CANCELLED);
            }
            return;
        }
        if (order.followDelivery(reported, clock.instant())) {
            log.info("Order {} status updated to {} after delivery event", order.getId(), reported);
        } else {
            log.info("Order {} stays {}, delivery reported {}", order.getId(), order.getStatus(), reported);
        }
    }

    private Order findWithItemsOrThrow(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + id + " was not found"));
    }

    private Page<OrderDto> withItems(Page<Long> ids) {
        Map<Long, Order> ordersById = orderRepository.findWithItemsByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.map(id -> orderMapper.toDto(ordersById.get(id)));
    }

    private OrderCreatedEvent orderCreatedEvent(Order order, long sequence, GeoLocation restaurantLocation) {
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
                .setRestaurantLocation(restaurantLocation == null
                        ? null
                        : new GeoPoint(restaurantLocation.latitude(), restaurantLocation.longitude()))
                .setDeliveryLocation(order.getDestination() == null
                        ? null
                        : new GeoPoint(order.getDestination().latitude().doubleValue(),
                        order.getDestination().longitude().doubleValue()))
                .build();
    }
}
