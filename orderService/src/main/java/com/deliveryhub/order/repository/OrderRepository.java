package com.deliveryhub.order.repository;

import com.deliveryhub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "orderItems")
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Page<Order> findAll(Pageable pageable);
}
