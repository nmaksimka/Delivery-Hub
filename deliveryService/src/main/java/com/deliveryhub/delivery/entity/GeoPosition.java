package com.deliveryhub.delivery.entity;

import org.locationtech.jts.geom.Point;

public record GeoPosition(double latitude, double longitude) {

    public GeoPosition {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    static GeoPosition of(Point point) {
        return point == null ? null : new GeoPosition(point.getY(), point.getX());
    }
}
