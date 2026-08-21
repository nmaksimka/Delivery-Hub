package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record RestaurantRequest(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @Size(max = 255) String address,
        @Size(max = 32) String phone,
        LocalTime openTime,
        LocalTime closeTime,
        Boolean active,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
