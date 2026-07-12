package com.deliveryhub.delivery;

import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.service.DeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class DeliveryMetricsIT {

    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void countsStatusChangeOnlyAfterCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        double before = assigned();

        transaction.executeWithoutResult(status -> {
            deliveryService.createDelivery(new CreateDeliveryRequest(9101L, 42L, null, null));
            assertThat(assigned()).isEqualTo(before);
        });

        assertThat(assigned()).isEqualTo(before + 1);
    }

    @Test
    void rolledBackChangeIsNotCounted() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        double before = assigned();

        transaction.executeWithoutResult(status -> {
            deliveryService.createDelivery(new CreateDeliveryRequest(9102L, 42L, null, null));
            status.setRollbackOnly();
        });

        assertThat(assigned()).isEqualTo(before);
    }

    private double assigned() {
        Counter counter = meterRegistry.find(DeliveryService.STATUS_CHANGED)
                .tag(DeliveryService.STATUS_TAG, "ASSIGNED").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
