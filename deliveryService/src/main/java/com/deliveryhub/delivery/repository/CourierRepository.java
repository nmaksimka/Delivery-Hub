package com.deliveryhub.delivery.repository;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    Optional<Courier> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courier c where c.id = :id")
    Optional<Courier> lockById(Long id);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Courier c SET c.ratingSum = c.ratingSum + :rating, c.ratingCount = c.ratingCount + 1 WHERE c.id = :courierId")
    int addRating(long courierId, int rating);
}
