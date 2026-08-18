package com.deliveryhub.delivery.quote;

public record RoutePath(Polyline line, int durationSeconds, Route.RouteSource source) {

    public double metersPerSecond() {
        if (durationSeconds <= 0) {
            return 0;
        }
        return line.lengthMeters() / durationSeconds;
    }
}
