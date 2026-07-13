package com.deliveryhub.restaurant.importer;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportJobTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void countsProgressInPercent() {
        ImportJob job = ImportJob.queued(ImportSource.MEALDB, NOW);
        job.start(NOW);
        job.plan(4);

        job.recordItem(true);
        job.recordItem(false);
        job.recordSkipped();

        assertThat(job.progressPercent()).isEqualTo(75);
        assertThat(job.getItemsCreated()).isEqualTo(1);
        assertThat(job.getItemsUpdated()).isEqualTo(1);
        assertThat(job.getProcessed()).isEqualTo(3);
    }

    @Test
    void completedJobIsAlwaysHundredPercent() {
        ImportJob job = ImportJob.queued(ImportSource.MEALDB, NOW);
        job.start(NOW);

        job.complete(NOW.plusSeconds(5));

        assertThat(job.progressPercent()).isEqualTo(100);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(job.getFinishedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void failureKeepsShortenedReason() {
        ImportJob job = ImportJob.queued(ImportSource.MEALDB, NOW);

        job.fail("x".repeat(3000), NOW);

        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getError()).hasSize(1000);
    }

    @Test
    void cannotBeStartedTwice() {
        ImportJob job = ImportJob.queued(ImportSource.MEALDB, NOW);
        job.start(NOW);

        assertThatThrownBy(() -> job.start(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
