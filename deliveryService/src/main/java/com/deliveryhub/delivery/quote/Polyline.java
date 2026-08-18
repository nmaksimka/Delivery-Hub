package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;

import java.util.List;

public final class Polyline {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final List<GeoPosition> points;
    private final double[] distances;

    private Polyline(List<GeoPosition> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Polyline needs at least one point");
        }
        this.points = List.copyOf(points);
        this.distances = new double[this.points.size()];
        for (int i = 1; i < this.points.size(); i++) {
            distances[i] = distances[i - 1] + meters(this.points.get(i - 1), this.points.get(i));
        }
    }

    public static Polyline of(List<GeoPosition> points) {
        return new Polyline(points);
    }

    public static Polyline straight(GeoPosition from, GeoPosition to) {
        return new Polyline(List.of(from, to));
    }

    public double lengthMeters() {
        return distances[distances.length - 1];
    }

    public GeoPosition start() {
        return points.getFirst();
    }

    public GeoPosition end() {
        return points.getLast();
    }

    public List<GeoPosition> points() {
        return points;
    }

    public PathPoint pointAt(double travelledMeters) {
        if (points.size() == 1) {
            return new PathPoint(points.getFirst(), 0);
        }
        double at = Math.clamp(travelledMeters, 0, lengthMeters());
        int segment = segmentContaining(at);
        GeoPosition from = points.get(segment);
        GeoPosition to = points.get(segment + 1);
        double segmentLength = distances[segment + 1] - distances[segment];
        double fraction = segmentLength == 0 ? 1 : (at - distances[segment]) / segmentLength;
        GeoPosition position = fraction >= 1 ? to : new GeoPosition(
                from.latitude() + (to.latitude() - from.latitude()) * fraction,
                from.longitude() + (to.longitude() - from.longitude()) * fraction);
        return new PathPoint(position, heading(from, to));
    }

    public double remainingFrom(GeoPosition position) {
        if (points.size() == 1) {
            return meters(position, points.getFirst());
        }
        double bestOffset = meters(position, points.getLast());
        double bestRemaining = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            GeoPosition from = points.get(i);
            GeoPosition to = points.get(i + 1);
            double fraction = projection(position, from, to);
            GeoPosition foot = new GeoPosition(
                    from.latitude() + (to.latitude() - from.latitude()) * fraction,
                    from.longitude() + (to.longitude() - from.longitude()) * fraction);
            double offset = meters(position, foot);
            if (offset < bestOffset) {
                bestOffset = offset;
                double along = distances[i] + (distances[i + 1] - distances[i]) * fraction;
                bestRemaining = lengthMeters() - along;
            }
        }
        return bestRemaining + bestOffset;
    }

    public static double meters(GeoPosition from, GeoPosition to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double deltaLatitude = toLatitude - fromLatitude;
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double heading(GeoPosition from, GeoPosition to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(deltaLongitude) * Math.cos(toLatitude);
        double x = Math.cos(fromLatitude) * Math.sin(toLatitude)
                - Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(deltaLongitude);
        if (x == 0 && y == 0) {
            return 0;
        }
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private int segmentContaining(double at) {
        for (int i = 1; i < distances.length; i++) {
            if (at <= distances[i]) {
                return i - 1;
            }
        }
        return distances.length - 2;
    }

    private static double projection(GeoPosition position, GeoPosition from, GeoPosition to) {
        double scale = Math.cos(Math.toRadians((from.latitude() + to.latitude()) / 2));
        double segmentX = (to.longitude() - from.longitude()) * scale;
        double segmentY = to.latitude() - from.latitude();
        double pointX = (position.longitude() - from.longitude()) * scale;
        double pointY = position.latitude() - from.latitude();
        double squaredLength = segmentX * segmentX + segmentY * segmentY;
        if (squaredLength == 0) {
            return 0;
        }
        return Math.clamp((pointX * segmentX + pointY * segmentY) / squaredLength, 0, 1);
    }

    public record PathPoint(GeoPosition position, double heading) {
    }
}
