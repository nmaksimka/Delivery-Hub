package com.deliveryhub.restaurant.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false)
    private byte[] payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Transient
    private boolean stored;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateId, String topic, byte[] payload, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markPublished(Instant at) {
        if (publishedAt != null) {
            throw new IllegalStateException("Outbox event " + id + " is already published");
        }
        publishedAt = Objects.requireNonNull(at, "at");
    }

    @PostPersist
    @PostLoad
    void markStored() {
        stored = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
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
}
