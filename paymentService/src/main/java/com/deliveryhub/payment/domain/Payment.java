package com.deliveryhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "provider_reference", length = 64, updatable = false)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason", length = 64, updatable = false)
    private ErrorCode declineReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(long orderId, long userId, BigDecimal amount, PaymentStatus status, String provider,
                    String providerReference, ErrorCode declineReason, Instant now) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = Money.positive(amount);
        this.status = status;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerReference = providerReference;
        this.declineReason = declineReason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Payment held(long orderId, long userId, BigDecimal amount, String provider, String reference,
                               Instant now) {
        return new Payment(orderId, userId, amount, PaymentStatus.HELD, provider,
                Objects.requireNonNull(reference, "reference"), null, now);
    }

    public static Payment rejected(long orderId, long userId, BigDecimal amount, String provider, ErrorCode reason,
                                   Instant now) {
        return new Payment(orderId, userId, amount, PaymentStatus.REJECTED, provider, null,
                Objects.requireNonNull(reason, "reason"), now);
    }

    public boolean capture(Instant now) {
        return moveTo(PaymentStatus.CAPTURED, PaymentStatus.HELD, now);
    }

    public boolean release(Instant now) {
        return moveTo(PaymentStatus.RELEASED, PaymentStatus.HELD, now);
    }

    public boolean refund(Instant now) {
        return moveTo(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED, now);
    }

    public void requireSameRequest(long otherUserId, BigDecimal otherAmount) {
        if (userId != otherUserId || amount.compareTo(otherAmount) != 0) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_MISMATCH,
                    "Order " + orderId + " already has a payment with another user or amount");
        }
    }

    public boolean isRejected() {
        return status == PaymentStatus.REJECTED;
    }

    public boolean isVisibleTo(long viewerId, boolean admin) {
        return admin || userId == viewerId;
    }

    private boolean moveTo(PaymentStatus target, PaymentStatus required, Instant now) {
        if (status == target) {
            return false;
        }
        if (status != required) {
            throw new PaymentException(ErrorCode.PAYMENT_INVALID_STATE,
                    "Payment of order " + orderId + " is " + status + ", cannot become " + target);
        }
        status = target;
        updatedAt = now;
        return true;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public ErrorCode getDeclineReason() {
        return declineReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
