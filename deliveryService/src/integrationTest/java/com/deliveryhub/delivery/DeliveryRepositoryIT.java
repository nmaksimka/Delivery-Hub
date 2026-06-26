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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class DeliveryRepositoryIT {

    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void findsDeliveryByOrderId() {
        deliveryRepository.saveAndFlush(Delivery.forOrder(1001L));
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
        deliveryRepository.saveAndFlush(Delivery.forOrder(1002L));

        assertThatThrownBy(() -> deliveryRepository.saveAndFlush(Delivery.forOrder(1002L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsStatusAndCourier() {
        Delivery delivery = deliveryRepository.saveAndFlush(Delivery.forOrder(1003L));

        delivery.assignCourier(55L);
        delivery.changeStatus(DeliveryStatus.PICKED_UP);
        deliveryRepository.flush();
        entityManager.clear();

        Delivery loaded = deliveryRepository.findByOrderId(1003L).orElseThrow();
        assertThat(loaded.getCourierId()).isEqualTo(55L);
        assertThat(loaded.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
    }
}
