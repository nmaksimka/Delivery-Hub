package com.deliveryhub.user.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    private static final int SCALE = 6;

    public GeoPoint {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0 || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Coordinates out of range: " + latitude + ", " + longitude);
        }
        latitude = latitude.setScale(SCALE, RoundingMode.HALF_UP);
        longitude = longitude.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
