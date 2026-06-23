package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record RestaurantRequest(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @Size(max = 255) String address,
        @Size(max = 32) String phone,
        LocalTime openTime,
        LocalTime closeTime,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
