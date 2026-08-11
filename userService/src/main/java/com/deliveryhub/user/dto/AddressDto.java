package com.deliveryhub.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AddressDto(
        Long id,
        String label,
        String city,
        String street,
        String house,
        String apartment,
        String entrance,
        String floor,
        String courierComment,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean primary,
        Instant createdAt) {
}
