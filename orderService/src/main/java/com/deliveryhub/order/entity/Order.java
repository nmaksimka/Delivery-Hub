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

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "promo_code", length = 64)
    private String promoCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 32)
    private CancellationReason cancellationReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Column(name = "last_delivery_sequence", nullable = false)
    private long lastDeliverySequence;

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
        this.discount = BigDecimal.ZERO;
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

    public void applyDiscount(String code, BigDecimal amount) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(amount, "amount");
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Discount can be applied only to a new order, current status: " + status);
        }
        if (promoCode != null) {
            throw new IllegalStateException("Order already has promo code " + promoCode);
        }
        if (amount.signum() < 0 || amount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("Discount must be between 0 and the order amount " + totalAmount);
        }
        promoCode = code;
        discount = amount;
        totalAmount = totalAmount.subtract(amount);
    }

    public BigDecimal subtotal() {
        return totalAmount.add(discount);
    }

    public boolean hasPromo() {
        return promoCode != null;
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
        if (next == OrderStatus.CANCELLED) {
            return cancel(CancellationReason.ADMIN, at);
        }
        status = next;
        updatedAt = at;
        return true;
    }

    public boolean cancelByCustomer(Instant at) {
        if (status.isFinal() || status == OrderStatus.IN_DELIVERY) {
            throw new InvalidStatusTransitionException(id, status, OrderStatus.CANCELLED);
        }
        return cancel(CancellationReason.CUSTOMER, at);
    }

    public boolean cancel(CancellationReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (status == OrderStatus.DELIVERED) {
            throw new InvalidStatusTransitionException(id, status, OrderStatus.CANCELLED);
        }
        status = OrderStatus.CANCELLED;
        cancellationReason = reason;
        updatedAt = at;
        return true;
    }

    public boolean markPaymentHeld(Instant at) {
        return advanceTo(OrderStatus.PAYMENT_HELD, at);
    }

    public boolean markCourierAssigned(Instant at) {
        return advanceTo(OrderStatus.COURIER_ASSIGNED, at);
    }

    public boolean confirmPayment(Instant at) {
        return advanceTo(OrderStatus.CONFIRMED, at);
    }

    public boolean recordRefund(Instant at) {
        if (status != OrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(id, status, OrderStatus.CANCELLED);
        }
        if (refundedAt != null) {
            return false;
        }
        refundedAt = Objects.requireNonNull(at, "at");
        return true;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    public boolean isStuckIn(OrderStatus awaited, Instant cutoff) {
        return status == awaited && awaited.isAwaitingSaga() && updatedAt.isBefore(cutoff);
    }

    public boolean followDelivery(OrderStatus reported, Instant at) {
        Objects.requireNonNull(reported, "reported");
        if (reported == OrderStatus.CANCELLED || !paidEnoughFor(reported)) {
            return false;
        }
        return advanceTo(reported, at);
    }

    private boolean paidEnoughFor(OrderStatus reported) {
        OrderStatus required = reported.isBefore(OrderStatus.IN_DELIVERY) ? OrderStatus.PAYMENT_HELD : OrderStatus.CONFIRMED;
        return !status.isBefore(required);
    }

    private boolean advanceTo(OrderStatus next, Instant at) {
        Objects.requireNonNull(at, "at");
        if (status.isFinal() || !status.isBefore(next)) {
            return false;
        }
        status = next;
        updatedAt = at;
        return true;
    }

    public long nextEventSequence() {
        eventSequence++;
        return eventSequence;
    }

    public boolean acceptDeliveryEvent(long sequence) {
        if (sequence <= 0) {
            return true;
        }
        if (sequence <= lastDeliverySequence) {
            return false;
        }
        lastDeliverySequence = sequence;
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

    public BigDecimal getDiscount() {
        return discount;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public Instant getRefundedAt() {
        return refundedAt;
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
