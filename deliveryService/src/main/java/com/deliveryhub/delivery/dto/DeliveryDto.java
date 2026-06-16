package com.deliveryhub.delivery.dto;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryDto {
    private Long id;
    private Long orderId;
    private Long courierId;
    private DeliveryStatus status;
    private Instant estimatedDeliveryTime;
    private Instant createdAt;
    private Instant updatedAt;
}
