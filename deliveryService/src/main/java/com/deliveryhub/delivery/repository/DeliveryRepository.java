package com.deliveryhub.delivery.repository;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Query("""
            SELECT DISTINCT d.courierId FROM Delivery d
            WHERE d.courierId IN :courierIds AND d.status NOT IN :finished
            """)
    List<Long> findBusyCouriers(Collection<Long> courierIds, Collection<DeliveryStatus> finished);

    @Override
    Page<Delivery> findAll(Pageable pageable);
}
