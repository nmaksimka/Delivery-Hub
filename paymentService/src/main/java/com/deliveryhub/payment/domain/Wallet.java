package com.deliveryhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal available;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal held;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    private Wallet(Long userId, Instant now) {
        this.userId = userId;
        this.available = Money.zero();
        this.held = Money.zero();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Wallet openFor(long userId, Instant now) {
        return new Wallet(userId, now);
    }

    public void topUp(BigDecimal amount, BigDecimal maxBalance, Instant now) {
        BigDecimal value = Money.positive(amount);
        if (available.add(held).add(value).compareTo(maxBalance) > 0) {
            throw new PaymentException(ErrorCode.WALLET_LIMIT_EXCEEDED,
                    "Demo wallet can hold at most " + maxBalance.toPlainString());
        }
        available = available.add(value);
        updatedAt = now;
    }

    public boolean canHold(BigDecimal amount) {
        return available.compareTo(Money.positive(amount)) >= 0;
    }

    public void hold(BigDecimal amount, Instant now) {
        if (!canHold(amount)) {
            throw new PaymentException(ErrorCode.PAYMENT_INSUFFICIENT_FUNDS, "Not enough money in the wallet");
        }
        BigDecimal value = Money.positive(amount);
        available = available.subtract(value);
        held = held.add(value);
        updatedAt = now;
    }

    public void captureHeld(BigDecimal amount, Instant now) {
        held = held.subtract(heldPart(amount));
        updatedAt = now;
    }

    public void releaseHeld(BigDecimal amount, Instant now) {
        BigDecimal value = heldPart(amount);
        held = held.subtract(value);
        available = available.add(value);
        updatedAt = now;
    }

    public void refund(BigDecimal amount, Instant now) {
        available = available.add(Money.positive(amount));
        updatedAt = now;
    }

    private BigDecimal heldPart(BigDecimal amount) {
        BigDecimal value = Money.positive(amount);
        if (held.compareTo(value) < 0) {
            throw new PaymentException(ErrorCode.PAYMENT_INVALID_STATE, "Wallet does not hold " + value.toPlainString());
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getHeld() {
        return held;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
