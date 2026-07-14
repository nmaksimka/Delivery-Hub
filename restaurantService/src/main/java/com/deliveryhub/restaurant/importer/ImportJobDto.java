package com.deliveryhub.restaurant.importer;

import java.time.Instant;
import java.util.UUID;

public record ImportJobDto(
        UUID id,
        ImportSource source,
        ImportStatus status,
        int total,
        int processed,
        int progressPercent,
        int restaurantsCreated,
        int itemsCreated,
        int itemsUpdated,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static ImportJobDto of(ImportJob job) {
        return new ImportJobDto(job.getId(), job.getSource(), job.getStatus(), job.getTotal(), job.getProcessed(),
                job.progressPercent(), job.getRestaurantsCreated(), job.getItemsCreated(), job.getItemsUpdated(),
                job.getError(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
    }
}
