package com.deliveryhub.delivery;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.tracking.TrackingUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class, CourierSimulatorIT.Recorder.class})
class CourierSimulatorIT {

    private static final GeoPosition RESTAURANT = new GeoPosition(56.4884, 84.9480);
    private static final GeoPosition CUSTOMER = new GeoPosition(56.4697, 84.9483);

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private Recorder recorder;

    @DynamicPropertySource
    static void fastSimulator(DynamicPropertyRegistry registry) {
        registry.add("app.simulator.enabled", () -> "true");
        registry.add("app.simulator.tick", () -> "200ms");
        registry.add("app.simulator.speed-kmh", () -> "3600");
        registry.add("app.simulator.pickup-delay", () -> "0s");
        registry.add("app.routing.base-url", () -> "http://127.0.0.1:9");
        registry.add("app.routing.connect-timeout", () -> "200ms");
    }

    @Test
    void simulatedCourierDrivesConfirmedOrderToCustomerAndDeliversIt() {
        Instant now = Instant.now();
        Courier courier = Courier.register(null, "Симулятор", "+79990001234", now);
        courier.occupy(now);
        courier = courierRepository.saveAndFlush(courier);
        Delivery delivery = Delivery.forOrder(990_001L, 42L, courier.getId(), null, now);
        delivery.routeBetween(RESTAURANT, CUSTOMER);
        delivery.markOrderConfirmed(now.minusSeconds(60));
        long deliveryId = deliveryRepository.saveAndFlush(delivery).getId();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveryRepository.findById(deliveryId)).get()
                        .extracting(Delivery::getStatus).isEqualTo(DeliveryStatus.DELIVERED));

        List<TrackingUpdate> updates = recorder.updates.stream().filter(update -> update.deliveryId() == deliveryId).toList();
        assertThat(updates).filteredOn(update -> update.type() == TrackingUpdate.Type.STATUS)
                .extracting(TrackingUpdate::status)
                .containsExactly(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED);
        List<TrackingUpdate> positions = updates.stream()
                .filter(update -> update.type() == TrackingUpdate.Type.POSITION).toList();
        assertThat(positions).hasSizeGreaterThanOrEqualTo(2);
        assertThat(positions).extracting(TrackingUpdate::etaSeconds).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(positions.getLast().remainingMeters()).isZero();
        assertThat(positions.getLast().courier().latitude()).isEqualTo(CUSTOMER.latitude());
        assertThat(courierRepository.findById(courier.getId())).get()
                .satisfies(found -> {
                    assertThat(found.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
                    assertThat(found.getPosition()).isEqualTo(CUSTOMER);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recorder {

        private final List<TrackingUpdate> updates = new CopyOnWriteArrayList<>();

        @EventListener
        void record(TrackingUpdate update) {
            updates.add(update);
        }
    }
}
