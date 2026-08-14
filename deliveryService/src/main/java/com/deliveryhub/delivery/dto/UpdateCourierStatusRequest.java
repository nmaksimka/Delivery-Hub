package com.deliveryhub.delivery.dto;

import com.deliveryhub.delivery.entity.CourierStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCourierStatusRequest(@NotNull CourierStatus status) {
}
