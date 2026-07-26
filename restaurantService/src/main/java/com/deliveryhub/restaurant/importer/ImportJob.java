package com.deliveryhub.restaurant.importer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int processed;

    @Column(name = "restaurants_created", nullable = false)
    private int restaurantsCreated;

    @Column(name = "items_created", nullable = false)
    private int itemsCreated;

    @Column(name = "items_updated", nullable = false)
    private int itemsUpdated;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "instance_id", length = 128)
    private String instanceId;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    protected ImportJob() {
    }

    private ImportJob(ImportSource source, Instant createdAt, String instanceId) {
        this.id = UUID.randomUUID();
        this.source = Objects.requireNonNull(source, "source");
        this.status = ImportStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.heartbeatAt = createdAt;
    }

    public static ImportJob queued(ImportSource source, Instant createdAt, String instanceId) {
        return new ImportJob(source, createdAt, instanceId);
    }

    public void beat(Instant at) {
        heartbeatAt = at;
    }

    public boolean belongsTo(String instance) {
        return instance.equals(instanceId);
    }

    public boolean isSilentSince(Instant moment) {
        return heartbeatAt == null || heartbeatAt.isBefore(moment);
    }

    public void start(Instant at) {
        if (status != ImportStatus.PENDING) {
            throw new IllegalStateException("Import job " + id + " is already " + status);
        }
        status = ImportStatus.RUNNING;
        startedAt = at;
    }

    public void plan(int total) {
        this.total = total;
    }

    public void recordRestaurantCreated() {
        restaurantsCreated++;
    }

    public void recordItem(boolean created) {
        processed++;
        if (created) {
            itemsCreated++;
        } else {
            itemsUpdated++;
        }
    }

    public void recordSkipped() {
        processed++;
    }

    public void complete(Instant at) {
        status = ImportStatus.COMPLETED;
        finishedAt = at;
    }

    public void fail(String reason, Instant at) {
        status = ImportStatus.FAILED;
        error = reason != null && reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason;
        finishedAt = at;
    }

    public int progressPercent() {
        if (status == ImportStatus.COMPLETED) {
            return 100;
        }
        return total == 0 ? 0 : Math.min(100, processed * 100 / total);
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getTotal() {
        return total;
    }

    public int getProcessed() {
        return processed;
    }

    public int getRestaurantsCreated() {
        return restaurantsCreated;
    }

    public int getItemsCreated() {
        return itemsCreated;
    }

    public int getItemsUpdated() {
        return itemsUpdated;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }
}
