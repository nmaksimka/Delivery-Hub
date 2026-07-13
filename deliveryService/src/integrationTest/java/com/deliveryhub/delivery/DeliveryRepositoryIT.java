package com.deliveryhub.delivery;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class DeliveryRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void findsDeliveryByOrderId() {
        deliveryRepository.saveAndFlush(Delivery.forOrder(1001L, 42L, NOW));
        entityManager.clear();

        assertThat(deliveryRepository.findByOrderId(1001L)).get()
                .satisfies(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
                    assertThat(delivery.getCreatedAt()).isNotNull();
                });
        assertThat(deliveryRepository.existsByOrderId(1001L)).isTrue();
        assertThat(deliveryRepository.existsByOrderId(9999L)).isFalse();
    }

    @Test
    void databaseAllowsOnlyOneDeliveryPerOrder() {
        deliveryRepository.saveAndFlush(Delivery.forOrder(1002L, 42L, NOW));

        assertThatThrownBy(() -> deliveryRepository.saveAndFlush(Delivery.forOrder(1002L, 42L, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsStatusAndCourier() {
        Delivery delivery = deliveryRepository.saveAndFlush(Delivery.forOrder(1003L, 42L, NOW));

        delivery.assignCourier(55L, NOW);
        delivery.changeStatus(DeliveryStatus.PICKED_UP, NOW);
        deliveryRepository.flush();
        entityManager.clear();

        Delivery loaded = deliveryRepository.findByOrderId(1003L).orElseThrow();
        assertThat(loaded.getCourierId()).isEqualTo(55L);
        assertThat(loaded.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void rejectsUpdateBasedOnStaleVersion() {
        Delivery stale = deliveryRepository.saveAndFlush(Delivery.forOrder(1004L, 42L, NOW));
        entityManager.detach(stale);

        Delivery fresh = deliveryRepository.findByOrderId(1004L).orElseThrow();
        fresh.changeStatus(DeliveryStatus.PICKED_UP, NOW);
        deliveryRepository.flush();

        stale.changeStatus(DeliveryStatus.CANCELLED, NOW);

        assertThatThrownBy(() -> deliveryRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
