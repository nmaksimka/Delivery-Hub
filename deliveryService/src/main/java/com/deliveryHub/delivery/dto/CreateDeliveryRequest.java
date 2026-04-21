package com.deliveryHub.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryRequest {
    @NotNull
    private Long orderId;

    private Long courierId;
    private LocalDateTime estimatedDeliveryTime;
}
