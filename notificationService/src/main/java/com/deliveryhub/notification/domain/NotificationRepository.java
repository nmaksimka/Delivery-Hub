package com.deliveryhub.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL
            """)
    int markRead(@Param("id") UUID id, @Param("userId") Long userId, @Param("now") Instant now);

    @Query(value = """
            SELECT * FROM notifications
            WHERE mail_status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> lockMailsDue(@Param("now") Instant now, @Param("limit") int limit);
}
