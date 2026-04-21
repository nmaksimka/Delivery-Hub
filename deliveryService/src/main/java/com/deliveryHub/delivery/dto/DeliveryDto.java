package com.deliveryHub.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryDto {
    private Long id;
    private Long orderId;
    private Long courierId;
    private String status;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
