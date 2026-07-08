package com.deliveryhub.order;

import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class OrderRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesOrderWithItemsAndTotal() {
        Long orderId = orderRepository.saveAndFlush(orderOf(42L)).getId();
        entityManager.clear();

        Order loaded = orderRepository.findWithItemsById(orderId).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getOrderItems())).isTrue();
        assertThat(loaded.getOrderItems()).hasSize(2);
        assertThat(loaded.getTotalAmount()).isEqualByComparingTo("34.00");
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsOnlyOrdersOfRequestedUser() {
        orderRepository.saveAndFlush(orderOf(501L));
        orderRepository.saveAndFlush(orderOf(501L));
        orderRepository.saveAndFlush(orderOf(502L));
        entityManager.clear();

        Page<Order> page = orderRepository.findByUserId(501L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(order -> assertThat(order.belongsTo(501L)).isTrue());
    }

    @Test
    void persistsStatusChange() {
        Order order = orderRepository.saveAndFlush(orderOf(42L));

        order.applyStatus(OrderStatus.CONFIRMED, NOW);
        orderRepository.flush();
        entityManager.clear();

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    private static Order orderOf(long userId) {
        Order order = Order.place(userId, 1L, NOW);
        order.addItem(10L, "Pizza", 2, new BigDecimal("12.50"));
        order.addItem(11L, "Cola", 3, new BigDecimal("3.00"));
        return order;
    }
}
