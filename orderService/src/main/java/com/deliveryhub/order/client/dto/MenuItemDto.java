package com.deliveryhub.order.client.dto;

import java.math.BigDecimal;

public record MenuItemDto(Long id, String name, BigDecimal price, boolean available) {
}
