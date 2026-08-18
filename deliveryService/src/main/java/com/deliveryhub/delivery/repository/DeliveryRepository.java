package com.deliveryhub.delivery.repository;

import com.deliveryhub.delivery.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Override
    Page<Delivery> findAll(Pageable pageable);

    @Query("""
            select d from Delivery d
            where d.courierId = :courierId
              and d.status not in (com.deliveryhub.contracts.enums.DeliveryStatus.DELIVERED,
                                   com.deliveryhub.contracts.enums.DeliveryStatus.CANCELLED)
            """)
    Optional<Delivery> findActiveByCourierId(Long courierId);

    @Query("""
            select d from Delivery d
            where d.status = com.deliveryhub.contracts.enums.DeliveryStatus.ASSIGNED
              and d.orderConfirmedAt <= :confirmedBefore
              and d.courierId in (select c.id from Courier c where c.userId is null)
            order by d.orderConfirmedAt
            """)
    List<Delivery> findSimulatedAwaitingPickup(Instant confirmedBefore);

    @Query("""
            select d from Delivery d
            where d.status in (com.deliveryhub.contracts.enums.DeliveryStatus.PICKED_UP,
                               com.deliveryhub.contracts.enums.DeliveryStatus.IN_TRANSIT)
              and d.courierId in (select c.id from Courier c where c.userId is null)
            order by d.id
            """)
    List<Delivery> findSimulatedOnTheWay();
}
