package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.RoutePath;

public record TrackedRoute(RoutePath path, double metersPerSecond) {

    public TrackedRoute {
        if (metersPerSecond <= 0) {
            throw new IllegalArgumentException("Courier speed must be positive");
        }
    }

    public Eta etaFrom(GeoPosition position) {
        return Eta.of(path.line().remainingFrom(position), metersPerSecond);
    }
}
