package com.deliveryhub.delivery.entity;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "courier_id")
    private Long courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(name = "estimated_delivery_time")
    private Instant estimatedDeliveryTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Delivery() {
    }

    private Delivery(Long orderId, Long courierId, Instant estimatedDeliveryTime, Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.courierId = courierId;
        this.estimatedDeliveryTime = estimatedDeliveryTime;
        this.status = DeliveryStatus.ASSIGNED;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static Delivery forOrder(Long orderId, Instant createdAt) {
        return new Delivery(orderId, null, null, createdAt);
    }

    public static Delivery forOrder(Long orderId, Long courierId, Instant estimatedDeliveryTime, Instant createdAt) {
        return new Delivery(orderId, courierId, estimatedDeliveryTime, createdAt);
    }

    public void changeStatus(DeliveryStatus next, Instant at) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(at, "at");
        if (next == status) {
            return;
        }
        if (isFinished() || !isForward(next)) {
            throw new IllegalStateException(
                    "Delivery " + id + " cannot change status from " + status + " to " + next);
        }
        status = next;
        updatedAt = at;
    }

    public void assignCourier(Long courierId, Instant at) {
        if (isFinished()) {
            throw new IllegalStateException("Delivery " + id + " is already " + status);
        }
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.updatedAt = Objects.requireNonNull(at, "at");
    }

    public boolean isFinished() {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED;
    }

    private boolean isForward(DeliveryStatus next) {
        return next == DeliveryStatus.CANCELLED || next.ordinal() > status.ordinal();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCourierId() {
        return courierId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getEstimatedDeliveryTime() {
        return estimatedDeliveryTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
