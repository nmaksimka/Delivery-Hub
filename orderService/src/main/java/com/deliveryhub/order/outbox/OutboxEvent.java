package com.deliveryhub.order.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false)
    private byte[] payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "parked_at")
    private Instant parkedAt;

    @Transient
    private boolean stored;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType,
                       byte[] payload, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markPublished(Instant at) {
        if (publishedAt != null) {
            throw new IllegalStateException("Outbox event " + id + " is already published");
        }
        publishedAt = Objects.requireNonNull(at, "at");
        nextAttemptAt = null;
    }

    public void postpone(String error, Instant now, Duration delay) {
        lastError = shortened(error);
        nextAttemptAt = now.plus(delay);
    }

    public void recordFailedAttempt(String error, Instant now, Duration delay, int maxAttempts) {
        attempts++;
        lastError = shortened(error);
        if (attempts >= maxAttempts) {
            parkedAt = now;
            nextAttemptAt = null;
        } else {
            nextAttemptAt = now.plus(delay);
        }
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean isParked() {
        return parkedAt != null;
    }

    public boolean isWaiting(Instant now) {
        return nextAttemptAt != null && now.isBefore(nextAttemptAt);
    }

    public String aggregateKey() {
        return aggregateType + ":" + aggregateId;
    }

    private static String shortened(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    @PostLoad
    @PostPersist
    void markStored() {
        stored = true;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getParkedAt() {
        return parkedAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof OutboxEvent event && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
