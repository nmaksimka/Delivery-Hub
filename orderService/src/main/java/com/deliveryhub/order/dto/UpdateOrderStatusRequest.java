package com.deliveryhub.order.dto;

import com.deliveryhub.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOrderStatusRequest {
    @NotNull(message = "Status is required")
    private OrderStatus status;
}
