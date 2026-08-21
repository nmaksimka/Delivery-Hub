package com.deliveryhub.delivery.tracking;

public record Eta(long remainingMeters, long seconds) {

    public static Eta of(double remainingMeters, double metersPerSecond) {
        if (metersPerSecond <= 0) {
            throw new IllegalArgumentException("Speed must be positive");
        }
        double meters = Math.max(0, remainingMeters);
        return new Eta(Math.round(meters), (long) Math.ceil(meters / metersPerSecond));
    }
}
