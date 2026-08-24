package com.deliveryhub.order.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderId(Long orderId);

    Page<Review> findByUserId(Long userId, Pageable pageable);
}
