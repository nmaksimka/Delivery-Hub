package com.deliveryhub.delivery.simulator;

import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.quote.Route;
import com.deliveryhub.delivery.quote.RoutePath;
import com.deliveryhub.delivery.tracking.Eta;
import com.deliveryhub.delivery.tracking.TrackedRoute;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TripTest {

    private static final GeoPosition RESTAURANT = new GeoPosition(56.4700, 84.9500);
    private static final GeoPosition CORNER = new GeoPosition(56.4790, 84.9500);
    private static final GeoPosition CUSTOMER = new GeoPosition(56.4790, 84.9662);
    private static final double TEN_METERS_PER_SECOND = 10;

    private final TrackedRoute route = new TrackedRoute(
            new RoutePath(Polyline.of(List.of(RESTAURANT, CORNER, CUSTOMER)), 240, Route.RouteSource.OSRM),
            TEN_METERS_PER_SECOND);

    @Test
    void courierMovesBySpeedTimesTick() {
        Trip trip = Trip.fromStart(route);

        Polyline.PathPoint point = trip.advance(Duration.ofSeconds(5));

        assertThat(trip.travelledMeters()).isCloseTo(50, within(1e-9));
        assertThat(point.position().latitude()).isGreaterThan(RESTAURANT.latitude());
        assertThat(point.heading()).isCloseTo(0, within(0.01));
        assertThat(trip.arrived()).isFalse();
    }

    @Test
    void etaShrinksWithTravelledPart() {
        Trip trip = Trip.fromStart(route);
        double length = route.path().line().lengthMeters();

        Eta atStart = route.etaFrom(RESTAURANT);
        Polyline.PathPoint afterMinute = trip.advance(Duration.ofSeconds(60));
        Eta later = route.etaFrom(afterMinute.position());

        assertThat(atStart.remainingMeters()).isEqualTo(Math.round(length));
        assertThat(atStart.seconds()).isEqualTo((long) Math.ceil(length / TEN_METERS_PER_SECOND));
        assertThat(later.remainingMeters()).isCloseTo(Math.round(length - 600), within(1L));
        assertThat(later.seconds()).isCloseTo(atStart.seconds() - 60, within(1L));
    }

    @Test
    void arrivesAtCustomerAndStopsThere() {
        Trip trip = Trip.fromStart(route);

        Polyline.PathPoint last = trip.advance(Duration.ofMinutes(10));

        assertThat(trip.arrived()).isTrue();
        assertThat(last.position()).isEqualTo(CUSTOMER);
        assertThat(route.etaFrom(last.position())).isEqualTo(new Eta(0, 0));
    }

    @Test
    void resumedTripContinuesFromCourierPosition() {
        Polyline.PathPoint middle = route.path().line().pointAt(1200);

        Trip trip = Trip.resumedAt(route, middle.position());

        assertThat(trip.travelledMeters()).isCloseTo(1200, within(1.0));
    }

    @Test
    void routeNeedsPositiveSpeed() {
        assertThatThrownBy(() -> new TrackedRoute(route.path(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Eta.of(100, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
