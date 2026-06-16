package com.deliveryhub.delivery.repository;

import com.deliveryhub.delivery.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Override
    Page<Delivery> findAll(Pageable pageable);
}
