package com.deliveryhub.order.client.dto;

import java.math.BigDecimal;
import java.util.StringJoiner;

public record UserAddressDto(
        Long id,
        String city,
        String street,
        String house,
        String apartment,
        String entrance,
        String floor,
        String courierComment,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean geocoded) {

    public boolean onMap() {
        return geocoded && latitude != null && longitude != null;
    }

    public String line() {
        StringJoiner line = new StringJoiner(", ").add(city).add(street).add(house);
        appendIfPresent(line, "кв. ", apartment);
        appendIfPresent(line, "подъезд ", entrance);
        appendIfPresent(line, "этаж ", floor);
        return line.toString();
    }

    private static void appendIfPresent(StringJoiner line, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            line.add(prefix + value);
        }
    }
}
