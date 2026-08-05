package com.deliveryhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_operations")
public class WalletOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private WalletOperationType type;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "available_after", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal availableAfter;

    @Column(name = "held_after", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal heldAfter;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletOperation() {
    }

    private WalletOperation(Wallet wallet, WalletOperationType type, BigDecimal amount, Long orderId,
                            String idempotencyKey, Instant now) {
        this.walletId = wallet.getId();
        this.type = type;
        this.amount = Money.positive(amount);
        this.availableAfter = wallet.getAvailable();
        this.heldAfter = wallet.getHeld();
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
    }

    public static WalletOperation topUp(Wallet wallet, BigDecimal amount, String idempotencyKey, Instant now) {
        return new WalletOperation(wallet, WalletOperationType.TOP_UP, amount, null, idempotencyKey, now);
    }

    public static WalletOperation forOrder(Wallet wallet, WalletOperationType type, long orderId, BigDecimal amount,
                                           Instant now) {
        if (type == WalletOperationType.TOP_UP) {
            throw new IllegalArgumentException("Top-up is not an order operation");
        }
        return new WalletOperation(wallet, type, amount, orderId, null, now);
    }

    public boolean hasSameAmount(BigDecimal other) {
        return amount.compareTo(other) == 0;
    }

    public WalletOperationType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAvailableAfter() {
        return availableAfter;
    }

    public BigDecimal getHeldAfter() {
        return heldAfter;
    }

    public Long getOrderId() {
        return orderId;
    }
}
