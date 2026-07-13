package com.deliveryhub.order.repository;

import com.deliveryhub.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsById(Long id);

    @Query("SELECT o.id FROM Order o WHERE o.userId = :userId")
    Page<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT o.id FROM Order o")
    Page<Long> findAllIds(Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id IN :ids")
    List<Order> findWithItemsByIdIn(@Param("ids") Collection<Long> ids);
}
