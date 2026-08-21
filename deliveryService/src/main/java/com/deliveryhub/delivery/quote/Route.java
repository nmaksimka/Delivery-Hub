package com.deliveryhub.delivery.quote;

public record Route(int distanceMeters, int durationSeconds, RouteSource source) {

    public enum RouteSource {
        OSRM,
        STRAIGHT_LINE
    }
}
