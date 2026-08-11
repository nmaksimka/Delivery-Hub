package com.deliveryhub.order.promo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {

    long countByPromoIdAndUserId(Long promoId, Long userId);

    Optional<PromoRedemption> findByOrderId(Long orderId);
}
