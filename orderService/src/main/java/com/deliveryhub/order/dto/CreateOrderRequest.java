package com.deliveryhub.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * userId сознательно отсутствует: он берётся из JWT (заголовок X-User-Id от гейтвея),
 * иначе любой авторизованный пользователь мог бы создать заказ от чужого имени.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {
    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> orderItemsRequest;
}
