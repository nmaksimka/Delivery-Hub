package com.deliveryhub.restaurant.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    boolean existsBySourceAndStatusIn(ImportSource source, Collection<ImportStatus> statuses);

    List<ImportJob> findByStatusIn(Collection<ImportStatus> statuses);

    List<ImportJob> findBySourceAndStatusIn(ImportSource source, Collection<ImportStatus> statuses);

    List<ImportJob> findTop20ByOrderByCreatedAtDesc();
}
