package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WorkingHoursRequest(
        @NotNull(message = "Opening time is required") LocalTime openTime,
        @NotNull(message = "Closing time is required") LocalTime closeTime) {
}
