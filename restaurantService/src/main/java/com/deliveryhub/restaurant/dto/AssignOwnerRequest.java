package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignOwnerRequest(
        @NotNull(message = "Owner id is required") @Positive(message = "Owner id must be positive") Long ownerId) {
}
