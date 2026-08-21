package com.deliveryhub.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    private BigDecimal amount;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_status", nullable = false)
    private MailStatus mailStatus;

    @Column(name = "mail_attempts", nullable = false)
    private int mailAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error")
    private String lastError;

    @Version
    private Long version;

    protected Notification() {
    }

    private Notification(UUID id, long userId, long orderId, NotificationType type, String title, String message,
                         BigDecimal amount, String reason, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.userId = userId;
        this.orderId = orderId;
        this.type = Objects.requireNonNull(type);
        this.title = title;
        this.message = message;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = now;
        this.mailStatus = MailStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public static Notification orderCreated(UUID id, long userId, long orderId, BigDecimal amount, Instant now) {
        return new Notification(id, userId, orderId, NotificationType.ORDER_CREATED,
                "Заказ №" + orderId + " принят",
                "Мы получили заказ на " + amount.toPlainString() + " ₽ и ждём подтверждения оплаты.",
                amount, null, now);
    }

    public static Notification paymentRejected(UUID id, long userId, long orderId, BigDecimal amount, String reason,
                                               Instant now) {
        return new Notification(id, userId, orderId, NotificationType.PAYMENT_REJECTED,
                "Оплата заказа №" + orderId + " не прошла",
                "Не удалось списать " + amount.toPlainString() + " ₽, заказ отменён.",
                amount, reason, now);
    }

    public static Notification deliveryRejected(UUID id, long userId, long orderId, String reason, Instant now) {
        return new Notification(id, userId, orderId, NotificationType.DELIVERY_REJECTED,
                "Не нашли курьера для заказа №" + orderId,
                "Доставка сейчас невозможна, деньги вернутся на кошелёк.",
                null, reason, now);
    }

    public static Notification courierAssigned(UUID id, long userId, long orderId, Instant now) {
        return new Notification(id, userId, orderId, NotificationType.COURIER_ASSIGNED,
                "Курьер едет за заказом №" + orderId,
                "Следить за курьером можно на странице заказа.",
                null, null, now);
    }

    public static Notification orderDelivered(UUID id, long userId, long orderId, Instant now) {
        return new Notification(id, userId, orderId, NotificationType.ORDER_DELIVERED,
                "Заказ №" + orderId + " доставлен",
                "Приятного аппетита! Будем рады отзыву о ресторане.",
                null, null, now);
    }

    public static Notification orderCancelled(UUID id, long userId, long orderId, Instant now) {
        return new Notification(id, userId, orderId, NotificationType.ORDER_CANCELLED,
                "Заказ №" + orderId + " отменён",
                "Если оплата уже прошла, деньги вернутся на кошелёк.",
                null, null, now);
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void mailSent(Instant now) {
        mailStatus = MailStatus.SENT;
        mailAttempts++;
        sentAt = now;
        lastError = null;
    }

    public void mailFailed(String error, Instant now, Duration backoff, Duration maxBackoff, int maxAttempts) {
        mailAttempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (mailAttempts >= maxAttempts) {
            mailStatus = MailStatus.FAILED;
            return;
        }
        Duration delay = backoff.multipliedBy(1L << Math.min(mailAttempts - 1, 20));
        nextAttemptAt = now.plus(delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public MailStatus getMailStatus() {
        return mailStatus;
    }

    public int getMailAttempts() {
        return mailAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }
}
