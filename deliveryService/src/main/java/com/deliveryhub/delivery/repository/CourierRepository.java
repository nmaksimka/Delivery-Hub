package com.deliveryhub.delivery.repository;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    Optional<Courier> findByUserId(Long userId);

    Page<Courier> findByStatus(CourierStatus status, Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT * FROM couriers
            WHERE status = 'AVAILABLE'
              AND position IS NOT NULL
              AND ST_DWithin(position, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius)
            ORDER BY ST_Distance(position, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography), id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    Optional<Courier> lockNearestAvailable(double latitude, double longitude, double radius);

    @Query(nativeQuery = true, value = """
            SELECT * FROM couriers
            WHERE status = 'AVAILABLE'
            ORDER BY updated_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    Optional<Courier> lockAnyAvailable();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE couriers
            SET position = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, updated_at = :at
            WHERE id = :courierId
            """)
    int moveTo(long courierId, double latitude, double longitude, Instant at);
}
