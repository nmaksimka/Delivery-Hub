package com.deliveryhub.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryRequest {
    @NotNull(message = "Order ID is required")
    private Long orderId;

    private Long courierId;
    private Instant estimatedDeliveryTime;
}
