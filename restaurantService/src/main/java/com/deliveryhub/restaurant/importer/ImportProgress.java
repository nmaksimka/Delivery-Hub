package com.deliveryhub.restaurant.importer;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class ImportProgress {

    private final ImportJobRepository importJobRepository;
    private final Clock clock;

    public ImportProgress(ImportJobRepository importJobRepository, Clock clock) {
        this.importJobRepository = importJobRepository;
        this.clock = clock;
    }

    @Transactional
    public void started(UUID jobId) {
        change(jobId, job -> job.start(clock.instant()));
    }

    @Transactional
    public void planned(UUID jobId, int total) {
        change(jobId, job -> job.plan(total));
    }

    @Transactional
    public void skipped(UUID jobId) {
        change(jobId, ImportJob::recordSkipped);
    }

    @Transactional
    public void completed(UUID jobId) {
        change(jobId, job -> job.complete(clock.instant()));
    }

    @Transactional
    public void failed(UUID jobId, String reason) {
        change(jobId, job -> job.fail(reason, clock.instant()));
    }

    @Transactional
    public void change(UUID jobId, Consumer<ImportJob> change) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Import job " + jobId + " was not found"));
        change.accept(job);
        job.beat(clock.instant());
    }
}
