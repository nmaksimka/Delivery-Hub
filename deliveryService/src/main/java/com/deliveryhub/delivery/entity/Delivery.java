package com.deliveryhub.delivery.entity;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.exception.DeliveryFinishedException;
import com.deliveryhub.delivery.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    @Column(name = "user_id")
    private Long userId;

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

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "dropoff_latitude")
    private Double dropoffLatitude;

    @Column(name = "dropoff_longitude")
    private Double dropoffLongitude;

    @Column(name = "order_confirmed_at")
    private Instant orderConfirmedAt;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Column(name = "last_order_sequence", nullable = false)
    private long lastOrderSequence;

    @Version
    @Column(nullable = false)
    private long version;

    protected Delivery() {
    }

    private Delivery(Long orderId, Long userId, Long courierId, Instant estimatedDeliveryTime, Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.userId = userId;
        this.courierId = courierId;
        this.estimatedDeliveryTime = estimatedDeliveryTime;
        this.status = DeliveryStatus.ASSIGNED;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static Delivery forOrder(Long orderId, Long userId, Instant createdAt) {
        return new Delivery(orderId, userId, null, null, createdAt);
    }

    public static Delivery forOrder(Long orderId, Long userId, Long courierId, Instant estimatedDeliveryTime,
                                    Instant createdAt) {
        return new Delivery(orderId, userId, courierId, estimatedDeliveryTime, createdAt);
    }

    public boolean changeStatus(DeliveryStatus next, Instant at) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(at, "at");
        if (next == status) {
            return false;
        }
        if (!isAllowed(next)) {
            throw new InvalidStatusTransitionException(id, status, next);
        }
        status = next;
        updatedAt = at;
        return true;
    }

    public void assignCourier(Long courierId, Instant at) {
        if (isFinished()) {
            throw new DeliveryFinishedException(id, status);
        }
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.updatedAt = Objects.requireNonNull(at, "at");
    }

    public void routeBetween(GeoPosition pickup, GeoPosition dropoff) {
        if (pickup != null) {
            pickupLatitude = pickup.latitude();
            pickupLongitude = pickup.longitude();
        }
        if (dropoff != null) {
            dropoffLatitude = dropoff.latitude();
            dropoffLongitude = dropoff.longitude();
        }
    }

    public boolean markOrderConfirmed(Instant at) {
        if (orderConfirmedAt != null || isFinished()) {
            return false;
        }
        orderConfirmedAt = Objects.requireNonNull(at, "at");
        return true;
    }

    public boolean isOnTheWay() {
        return status == DeliveryStatus.PICKED_UP || status == DeliveryStatus.IN_TRANSIT;
    }

    public long nextEventSequence() {
        eventSequence++;
        return eventSequence;
    }

    public boolean acceptOrderEvent(long sequence) {
        if (sequence <= 0) {
            return true;
        }
        if (sequence <= lastOrderSequence) {
            return false;
        }
        lastOrderSequence = sequence;
        return true;
    }

    public boolean belongsTo(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public boolean isFinished() {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED;
    }

    private boolean isAllowed(DeliveryStatus next) {
        if (isFinished()) {
            return false;
        }
        return next == DeliveryStatus.CANCELLED || next.ordinal() == status.ordinal() + 1;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
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

    public GeoPosition getPickup() {
        return pickupLatitude == null ? null : new GeoPosition(pickupLatitude, pickupLongitude);
    }

    public GeoPosition getDropoff() {
        return dropoffLatitude == null ? null : new GeoPosition(dropoffLatitude, dropoffLongitude);
    }

    public Instant getOrderConfirmedAt() {
        return orderConfirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
