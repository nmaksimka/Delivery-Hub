package com.deliveryhub.restaurant.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT pg_try_advisory_xact_lock(:lockKey)", nativeQuery = true)
    boolean tryLockRelay(@Param("lockKey") long lockKey);

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(String aggregateId);
}
