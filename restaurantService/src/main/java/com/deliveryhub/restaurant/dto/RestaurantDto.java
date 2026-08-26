package com.deliveryhub.restaurant.dto;

import java.time.LocalTime;

public record RestaurantDto(
        Long id,
        String name,
        String address,
        String phone,
        String cuisine,
        String city,
        String imageUrl,
        LocalTime openTime,
        LocalTime closeTime,
        boolean active,
        Double latitude,
        Double longitude,
        Double rating,
        int ratingCount) {
}
