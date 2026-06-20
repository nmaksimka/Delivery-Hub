package com.deliveryhub.restaurant.dto;

import java.time.LocalTime;

public record RestaurantDto(
        Long id,
        String name,
        String address,
        String phone,
        LocalTime openTime,
        LocalTime closeTime,
        boolean active) {
}
