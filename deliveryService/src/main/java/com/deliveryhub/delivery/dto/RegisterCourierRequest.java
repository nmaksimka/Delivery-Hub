package com.deliveryhub.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterCourierRequest(
        @Positive Long userId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String phone) {
}
