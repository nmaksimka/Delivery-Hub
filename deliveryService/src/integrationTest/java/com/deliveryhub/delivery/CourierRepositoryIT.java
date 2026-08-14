package com.deliveryhub.delivery;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.repository.CourierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class CourierRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final double FAR_LATITUDE = 61.0;
    private static final double FAR_LONGITUDE = 99.0;

    @Autowired
    private CourierRepository courierRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void seedKeepsOldCourierIdsAvailableInTomsk() {
        assertThat(courierRepository.findAllById(java.util.List.of(9001L, 9010L)))
                .hasSize(2)
                .allSatisfy(courier -> {
                    assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
                    assertThat(courier.getPosition().latitude()).isBetween(56.4, 56.6);
                    assertThat(courier.getPosition().longitude()).isBetween(84.8, 85.1);
                });
    }

    @Test
    void storesPositionAsGeographyPoint() {
        Courier courier = available("Пётр", FAR_LATITUDE, FAR_LONGITUDE);

        GeoPosition position = courierRepository.findById(courier.getId()).orElseThrow().getPosition();

        assertThat(position.latitude()).isCloseTo(FAR_LATITUDE, within(1e-9));
        assertThat(position.longitude()).isCloseTo(FAR_LONGITUDE, within(1e-9));
    }

    @Test
    void picksNearestAvailableCourierWithinRadius() {
        available("Дальний", FAR_LATITUDE + 0.02, FAR_LONGITUDE);
        Courier near = available("Ближний", FAR_LATITUDE + 0.005, FAR_LONGITUDE);
        Courier busy = available("Занятый", FAR_LATITUDE + 0.001, FAR_LONGITUDE);
        busy.occupy(NOW);
        entityManager.flush();

        assertThat(courierRepository.lockNearestAvailable(FAR_LATITUDE, FAR_LONGITUDE, 5_000))
                .get().extracting(Courier::getId).isEqualTo(near.getId());
    }

    @Test
    void ignoresCouriersOutsideRadius() {
        available("За городом", FAR_LATITUDE + 0.1, FAR_LONGITUDE);

        assertThat(courierRepository.lockNearestAvailable(FAR_LATITUDE, FAR_LONGITUDE, 5_000)).isEmpty();
    }

    private Courier available(String name, double latitude, double longitude) {
        Courier courier = Courier.register(null, name, "+79990000000", NOW);
        courier.goOnline(NOW);
        courierRepository.saveAndFlush(courier);
        courierRepository.moveTo(courier.getId(), latitude, longitude, NOW);
        return courierRepository.findById(courier.getId()).orElseThrow();
    }
}
