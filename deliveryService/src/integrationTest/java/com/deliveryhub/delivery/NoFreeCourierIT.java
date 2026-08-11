package com.deliveryhub.delivery;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.dispatch.CourierDispatch;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.dispatch.courier-ids=880001")
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class NoFreeCourierIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void rejectsDeliveryWhenTheOnlyCourierIsBusy() {
        long firstOrderId = 8801L;
        long secondOrderId = 8802L;

        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(firstOrderId), paymentHeld(firstOrderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(secondOrderId), paymentHeld(secondOrderId));

        Optional<DeliveryRejectedEvent> rejected = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.DELIVERY_REJECTED, DeliveryRejectedEvent.class,
                        event -> event.getOrderId() == secondOrderId);

        assertThat(rejected).isPresent();
        assertThat(rejected.get().getReason()).isEqualTo(CourierDispatch.NO_FREE_COURIER);
        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(firstOrderId));
        assertThat(deliveryRepository.findByOrderId(firstOrderId).orElseThrow().getCourierId()).isEqualTo(880001L);
        assertThat(deliveryRepository.existsByOrderId(secondOrderId)).isFalse();
    }

    private static PaymentHeldEvent paymentHeld(long orderId) {
        return new PaymentHeldEvent(UUID.randomUUID(), orderId, 42L, new BigDecimal("34.00"), "demo", Instant.now());
    }
}
