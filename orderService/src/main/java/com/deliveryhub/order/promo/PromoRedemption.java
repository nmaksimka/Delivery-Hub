package com.deliveryhub.order.promo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "promo_redemptions")
public class PromoRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promo_id", nullable = false, updatable = false)
    private Long promoId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal discount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromoRedemption() {
    }

    public PromoRedemption(Long promoId, Long userId, Long orderId, BigDecimal discount, Instant createdAt) {
        this.promoId = Objects.requireNonNull(promoId, "promoId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.discount = Objects.requireNonNull(discount, "discount");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getPromoId() {
        return promoId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
