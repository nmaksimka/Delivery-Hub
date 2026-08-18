package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PolylineTest {

    private static final GeoPosition SOUTH_WEST = new GeoPosition(56.4700, 84.9500);
    private static final GeoPosition NORTH_WEST = new GeoPosition(56.4790, 84.9500);
    private static final GeoPosition NORTH_EAST = new GeoPosition(56.4790, 84.9662);

    private final Polyline corner = Polyline.of(List.of(SOUTH_WEST, NORTH_WEST, NORTH_EAST));

    @Test
    void measuresLengthAlongAllSegments() {
        double north = Polyline.meters(SOUTH_WEST, NORTH_WEST);
        double east = Polyline.meters(NORTH_WEST, NORTH_EAST);

        assertThat(north).isCloseTo(1000.8, within(1.0));
        assertThat(east).isCloseTo(996.0, within(2.0));
        assertThat(corner.lengthMeters()).isCloseTo(north + east, within(0.001));
    }

    @Test
    void interpolatesPointAndHeadingInsideSegment() {
        double north = Polyline.meters(SOUTH_WEST, NORTH_WEST);

        Polyline.PathPoint halfway = corner.pointAt(north / 2);
        Polyline.PathPoint onSecondLeg = corner.pointAt(north + 10);

        assertThat(halfway.position().latitude()).isCloseTo(56.4745, within(1e-6));
        assertThat(halfway.position().longitude()).isCloseTo(84.9500, within(1e-9));
        assertThat(halfway.heading()).isCloseTo(0, within(0.01));
        assertThat(onSecondLeg.position().latitude()).isCloseTo(56.4790, within(1e-9));
        assertThat(onSecondLeg.heading()).isCloseTo(90, within(0.1));
    }

    @Test
    void clampsPointsBeforeStartAndAfterEnd() {
        assertThat(corner.pointAt(-50).position()).isEqualTo(SOUTH_WEST);
        assertThat(corner.pointAt(corner.lengthMeters() + 500).position()).isEqualTo(NORTH_EAST);
    }

    @Test
    void remainingDistanceFollowsRouteFromProjectedPoint() {
        double north = Polyline.meters(SOUTH_WEST, NORTH_WEST);
        GeoPosition onRoute = corner.pointAt(north + 300).position();
        GeoPosition besideRoute = new GeoPosition(56.4745, 84.9508);

        assertThat(corner.remainingFrom(onRoute)).isCloseTo(corner.lengthMeters() - north - 300, within(1.0));
        assertThat(corner.remainingFrom(NORTH_EAST)).isCloseTo(0, within(0.01));
        assertThat(corner.remainingFrom(besideRoute)).isCloseTo(corner.lengthMeters() - north / 2 + 49, within(2.0));
    }

    @Test
    void headingPointsToCompassDirections() {
        assertThat(Polyline.heading(NORTH_WEST, SOUTH_WEST)).isCloseTo(180, within(0.01));
        assertThat(Polyline.heading(NORTH_EAST, NORTH_WEST)).isCloseTo(270, within(0.1));
        assertThat(Polyline.heading(SOUTH_WEST, SOUTH_WEST)).isZero();
    }

    @Test
    void straightLineHasTwoPoints() {
        Polyline straight = Polyline.straight(SOUTH_WEST, NORTH_EAST);

        assertThat(straight.points()).containsExactly(SOUTH_WEST, NORTH_EAST);
        assertThat(straight.lengthMeters()).isCloseTo(Polyline.meters(SOUTH_WEST, NORTH_EAST), within(0.001));
    }
}
