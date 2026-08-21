package com.deliveryhub.delivery.entity;

import com.deliveryhub.delivery.exception.CourierBusyException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourierTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void newCourierStartsOffline() {
        Courier courier = Courier.register(77L, "Алексей", "+79990000001", NOW);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.OFFLINE);
        assertThat(courier.getUserId()).isEqualTo(77L);
        assertThat(courier.getPosition()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> Courier.register(null, " ", "+79990000001", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void goesOnlineAndOffline() {
        Courier courier = Courier.register(null, "Алексей", "+79990000001", NOW);

        courier.goOnline(LATER);
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(courier.getUpdatedAt()).isEqualTo(LATER);

        courier.goOffline(LATER);
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.OFFLINE);
    }

    @Test
    void busyCourierCannotChangeStatusHimself() {
        Courier courier = Courier.register(null, "Алексей", "+79990000001", NOW);
        courier.occupy(NOW);

        assertThatThrownBy(() -> courier.goOffline(LATER)).isInstanceOf(CourierBusyException.class);
        assertThatThrownBy(() -> courier.goOnline(LATER)).isInstanceOf(CourierBusyException.class);
    }

    @Test
    void releaseMakesBusyCourierAvailableAgain() {
        Courier courier = Courier.register(null, "Алексей", "+79990000001", NOW);
        courier.occupy(NOW);

        assertThat(courier.release(LATER)).isTrue();
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void releaseKeepsOfflineCourierOffline() {
        Courier courier = Courier.register(null, "Алексей", "+79990000001", NOW);

        assertThat(courier.release(LATER)).isFalse();
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.OFFLINE);
    }

    @Test
    void positionRejectsCoordinatesOutOfRange() {
        assertThatThrownBy(() -> new GeoPosition(91, 84.9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPosition(56.4, 181)).isInstanceOf(IllegalArgumentException.class);
    }
}
