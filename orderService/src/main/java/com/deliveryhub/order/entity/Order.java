package com.deliveryhub.order.entity;

import com.deliveryhub.order.exception.InvalidStatusTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    protected Order() {
    }

    private Order(Long userId, Long restaurantId, Instant placedAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = Objects.requireNonNull(placedAt, "placedAt");
        this.updatedAt = placedAt;
    }

    public static Order place(Long userId, Long restaurantId, Instant placedAt) {
        return new Order(userId, restaurantId, placedAt);
    }

    public void addItem(Long menuItemId, String itemName, int quantity, BigDecimal pricePerUnit) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Items can be added only to a new order, current status: " + status);
        }
        OrderItem item = new OrderItem(this, menuItemId, itemName, quantity, pricePerUnit);
        orderItems.add(item);
        totalAmount = totalAmount.add(item.lineTotal());
    }

    public boolean changeStatus(OrderStatus next, Instant at) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(at, "at");
        if (next == status) {
            return false;
        }
        if (!status.canChangeTo(next)) {
            throw new InvalidStatusTransitionException(id, status, next);
        }
        status = next;
        updatedAt = at;
        return true;
    }

    public boolean followDelivery(OrderStatus reported, Instant at) {
        Objects.requireNonNull(reported, "reported");
        Objects.requireNonNull(at, "at");
        if (status.isFinal() || !status.isBefore(reported)) {
            return false;
        }
        status = reported;
        updatedAt = at;
        return true;
    }

    public boolean belongsTo(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }
}
