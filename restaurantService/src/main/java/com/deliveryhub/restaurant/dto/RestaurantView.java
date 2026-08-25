package com.deliveryhub.restaurant.dto;

import java.time.LocalTime;

public record RestaurantView(
        Long id,
        String name,
        String address,
        String phone,
        String cuisine,
        String city,
        String externalImageUrl,
        String imageKey,
        LocalTime openTime,
        LocalTime closeTime,
        boolean active,
        Double latitude,
        Double longitude,
        Double rating,
        int ratingCount) {
}
