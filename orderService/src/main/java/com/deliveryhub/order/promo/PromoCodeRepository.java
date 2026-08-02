package com.deliveryhub.order.promo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    boolean existsByCode(String code);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PromoCode p SET p.usedCount = p.usedCount + 1
            WHERE p.id = :id AND (p.maxRedemptions IS NULL OR p.usedCount < p.maxRedemptions)
            """)
    int reserveUsage(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount - 1 WHERE p.id = :id AND p.usedCount > 0")
    int releaseUsage(@Param("id") Long id);
}
