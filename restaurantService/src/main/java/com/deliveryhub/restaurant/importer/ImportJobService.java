package com.deliveryhub.restaurant.importer;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportJobService {

    private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);
    private static final Set<ImportStatus> ACTIVE = Set.of(ImportStatus.PENDING, ImportStatus.RUNNING);

    private final ImportJobRepository importJobRepository;
    private final ImportProgress progress;
    private final TaskExecutor importExecutor;
    private final Clock clock;
    private final ImportInstance instance;
    private final ImportProperties properties;
    private final Map<ImportSource, CatalogImport> imports = new EnumMap<>(ImportSource.class);

    public ImportJobService(ImportJobRepository importJobRepository,
                            ImportProgress progress,
                            @Qualifier("importExecutor") TaskExecutor importExecutor,
                            Clock clock,
                            ImportInstance instance,
                            ImportProperties properties,
                            List<CatalogImport> catalogImports) {
        this.importJobRepository = importJobRepository;
        this.progress = progress;
        this.importExecutor = importExecutor;
        this.clock = clock;
        this.instance = instance;
        this.properties = properties;
        catalogImports.forEach(catalogImport -> imports.put(catalogImport.source(), catalogImport));
    }

    public ImportJobDto start(ImportSource source) {
        failSilentJobs(source);
        if (importJobRepository.existsBySourceAndStatusIn(source, ACTIVE)) {
            throw new ImportAlreadyRunningException(source);
        }
        ImportJob job;
        try {
            job = importJobRepository.saveAndFlush(ImportJob.queued(source, clock.instant(), instance.id()));
        } catch (DataIntegrityViolationException e) {
            throw new ImportAlreadyRunningException(source);
        }
        UUID jobId = job.getId();
        try {
            importExecutor.execute(() -> imports.get(source).run(jobId));
        } catch (TaskRejectedException e) {
            progress.failed(jobId, "Import queue is full");
            throw new ImportAlreadyRunningException(source);
        }
        log.info("Import job {} from {} is queued", jobId, source);
        return ImportJobDto.of(job);
    }

    @Transactional(readOnly = true)
    public ImportJobDto get(UUID jobId) {
        return importJobRepository.findById(jobId)
                .map(ImportJobDto::of)
                .orElseThrow(() -> new EntityNotFoundException("Import job " + jobId + " was not found"));
    }

    @Transactional(readOnly = true)
    public List<ImportJobDto> recent() {
        return importJobRepository.findTop20ByOrderByCreatedAtDesc().stream().map(ImportJobDto::of).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void failJobsInterruptedByRestart() {
        Instant staleBefore = clock.instant().minus(properties.staleAfter());
        importJobRepository.findByStatusIn(ACTIVE).stream()
                .filter(job -> job.belongsTo(instance.id()) || job.isSilentSince(staleBefore))
                .forEach(job -> {
                    log.warn("Import job {} of instance {} was interrupted", job.getId(), job.getInstanceId());
                    job.fail("Interrupted by service restart", clock.instant());
                });
    }

    private void failSilentJobs(ImportSource source) {
        Instant staleBefore = clock.instant().minus(properties.staleAfter());
        importJobRepository.findBySourceAndStatusIn(source, ACTIVE).stream()
                .filter(job -> job.isSilentSince(staleBefore))
                .forEach(job -> {
                    log.warn("Import job {} of instance {} stopped reporting progress", job.getId(),
                            job.getInstanceId());
                    progress.failed(job.getId(), "Instance stopped reporting progress");
                });
    }
}
