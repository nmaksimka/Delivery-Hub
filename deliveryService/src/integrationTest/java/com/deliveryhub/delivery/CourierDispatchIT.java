package com.deliveryhub.delivery;

import com.deliveryhub.contracts.events.GeoPoint;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.dispatch.CourierDispatch;
import com.deliveryhub.delivery.dispatch.DeliveryZones;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class CourierDispatchIT {

    @Autowired
    private CourierDispatch courierDispatch;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private CourierRepository courierRepository;
    @Autowired
    private DeliveryZones deliveryZones;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void assignsNearestAvailableCourierAroundRestaurant() {
        GeoPoint restaurant = new GeoPoint(62.0, 100.0);
        long far = courierAt(62.03, 100.0);
        long near = courierAt(62.005, 100.0);

        courierDispatch.onPaymentHeld(paymentHeld(95001L, 9101L, restaurant));

        assertThat(courierOf(95001L)).isEqualTo(near);
        assertThat(statusOf(near)).isEqualTo(CourierStatus.BUSY);
        assertThat(statusOf(far)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(deliveryZones.find(9101L)).get()
                .satisfies(zone -> assertThat(zone.maxDistanceMeters()).isEqualTo(5000));
    }

    @Test
    void rejectsOrderWhenEveryCourierIsOutsideZone() {
        GeoPoint restaurant = new GeoPoint(63.0, 100.0);
        long outside = courierAt(63.2, 100.0);

        courierDispatch.onPaymentHeld(paymentHeld(95002L, 9102L, restaurant));

        assertThat(deliveryRepository.existsByOrderId(95002L)).isFalse();
        assertThat(statusOf(outside)).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(jdbcClient.sql("""
                        SELECT count(*) FROM outbox_events
                        WHERE event_type = 'delivery-rejected' AND aggregate_id = '95002'
                        """)
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void parallelOrdersTakeDifferentCouriers() throws Exception {
        GeoPoint restaurant = new GeoPoint(64.0, 100.0);
        List<Long> couriers = LongStream.range(0, 4).mapToObj(i -> courierAt(64.0 + i * 0.002, 100.0)).toList();
        List<Long> orders = LongStream.range(95100L, 95106L).boxed().toList();

        ExecutorService pool = Executors.newFixedThreadPool(orders.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (Long orderId : orders) {
            results.add(pool.submit(() -> {
                start.await();
                courierDispatch.onPaymentHeld(paymentHeld(orderId, 9103L, restaurant));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<Long> assigned = deliveryRepository.findAll().stream()
                .filter(delivery -> orders.contains(delivery.getOrderId()))
                .map(Delivery::getCourierId)
                .toList();
        assertThat(assigned).hasSize(couriers.size()).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(couriers);
    }

    @Test
    void cancelledDeliveryFreesCourierForNextOrder() {
        GeoPoint restaurant = new GeoPoint(65.0, 100.0);
        long courier = courierAt(65.001, 100.0);
        courierDispatch.onPaymentHeld(paymentHeld(95201L, 9104L, restaurant));
        assertThat(courierOf(95201L)).isEqualTo(courier);

        deliveryService.cancelDeliveryOfOrder(new OrderCancelledEvent(UUID.randomUUID(), 95201L, 42L,
                Instant.now(), 2L));
        assertThat(statusOf(courier)).isEqualTo(CourierStatus.AVAILABLE);

        courierDispatch.onPaymentHeld(paymentHeld(95202L, 9104L, null));
        assertThat(courierOf(95202L)).isEqualTo(courier);
    }

    private long courierAt(double latitude, double longitude) {
        return jdbcClient.sql("""
                        INSERT INTO couriers (name, phone, status, position, updated_at)
                        VALUES ('Тестовый курьер', '+79990000000', 'AVAILABLE',
                                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, now())
                        RETURNING id
                        """)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .query(Long.class)
                .single();
    }

    private Long courierOf(long orderId) {
        return deliveryRepository.findByOrderId(orderId).orElseThrow().getCourierId();
    }

    private CourierStatus statusOf(long courierId) {
        return courierRepository.findById(courierId).orElseThrow().getStatus();
    }

    private static PaymentHeldEvent paymentHeld(long orderId, long restaurantId, GeoPoint location) {
        return new PaymentHeldEvent(UUID.randomUUID(), orderId, 42L, new BigDecimal("34.00"), "demo", Instant.now(),
                restaurantId, location, null);
    }
}
