package com.deliveryhub.delivery.simulator;

import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.tracking.TrackedRoute;

import java.time.Duration;

public final class Trip {

    private final TrackedRoute route;
    private double travelledMeters;

    private Trip(TrackedRoute route, double travelledMeters) {
        this.route = route;
        this.travelledMeters = Math.clamp(travelledMeters, 0, route.path().line().lengthMeters());
    }

    public static Trip fromStart(TrackedRoute route) {
        return new Trip(route, 0);
    }

    public static Trip resumedAt(TrackedRoute route, GeoPosition position) {
        Polyline line = route.path().line();
        return new Trip(route, line.lengthMeters() - line.remainingFrom(position));
    }

    public Polyline.PathPoint advance(Duration elapsed) {
        double meters = route.metersPerSecond() * elapsed.toMillis() / 1000.0;
        travelledMeters = Math.min(route.path().line().lengthMeters(), travelledMeters + meters);
        return route.path().line().pointAt(travelledMeters);
    }

    public boolean arrived() {
        return travelledMeters >= route.path().line().lengthMeters();
    }

    public double travelledMeters() {
        return travelledMeters;
    }
}
