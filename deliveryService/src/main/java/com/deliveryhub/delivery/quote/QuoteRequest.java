package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuoteRequest(@NotNull @Positive Long restaurantId, @NotNull GeoPosition from, @NotNull GeoPosition to) {
}
