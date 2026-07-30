package com.deliveryhub.restaurant.importer;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final ImportJobRepository repository = mock(ImportJobRepository.class);
    private final ImportProgress progress = mock(ImportProgress.class);
    private final ImportJobService service = new ImportJobService(repository, progress, new SyncTaskExecutor(),
            Clock.fixed(NOW, ZoneOffset.UTC), new ImportInstance("node-a"),
            new ImportProperties(Duration.ofMinutes(10)), List.of());

    @Test
    void restartFailsOwnAndSilentJobsButKeepsLiveJobsOfOtherInstances() {
        ImportJob own = running("node-a", NOW.minusSeconds(5));
        ImportJob otherAlive = running("node-b", NOW.minusSeconds(5));
        ImportJob otherSilent = running("node-c", NOW.minus(Duration.ofMinutes(30)));
        when(repository.findByStatusIn(anyCollection())).thenReturn(List.of(own, otherAlive, otherSilent));

        service.failJobsInterruptedByRestart();

        assertThat(own.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(otherAlive.getStatus()).isEqualTo(ImportStatus.RUNNING);
        assertThat(otherSilent.getStatus()).isEqualTo(ImportStatus.FAILED);
    }

    @Test
    void newImportFailsOnlySilentJobOfDeadInstance() {
        ImportJob silent = running("node-b", NOW.minus(Duration.ofMinutes(30)));
        ImportJob alive = running("node-c", NOW.minusSeconds(5));
        when(repository.findBySourceAndStatusIn(eq(ImportSource.MEALDB), anyCollection()))
                .thenReturn(List.of(silent, alive));
        when(repository.existsBySourceAndStatusIn(eq(ImportSource.MEALDB), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.start(ImportSource.MEALDB))
                .isInstanceOf(ImportAlreadyRunningException.class);

        verify(progress).failed(eq(silent.getId()), any());
        verify(progress, never()).failed(eq(alive.getId()), any());
    }

    private static ImportJob running(String instance, Instant lastBeat) {
        ImportJob job = ImportJob.queued(ImportSource.MEALDB, NOW.minus(Duration.ofHours(1)), instance);
        job.start(NOW.minus(Duration.ofHours(1)));
        job.beat(lastBeat);
        return job;
    }
}
