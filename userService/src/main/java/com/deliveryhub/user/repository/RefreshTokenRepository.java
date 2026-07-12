package com.deliveryhub.user.repository;

import com.deliveryhub.user.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            WITH RECURSIVE family AS (
                SELECT id, replaced_by
                FROM refresh_tokens
                WHERE id = :tokenId
                UNION ALL
                SELECT next.id, next.replaced_by
                FROM refresh_tokens next
                JOIN family ON next.id = family.replaced_by
            )
            UPDATE refresh_tokens
            SET revoked_at = :revokedAt
            WHERE id IN (SELECT id FROM family)
              AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeSuccessorsOf(@Param("tokenId") UUID tokenId, @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before OR t.revokedAt < :before")
    int deleteExpiredOrRevokedBefore(@Param("before") Instant before);
}
