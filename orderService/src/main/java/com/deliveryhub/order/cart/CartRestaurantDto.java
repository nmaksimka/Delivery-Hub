package com.deliveryhub.order.cart;

public record CartRestaurantDto(Long id, String name, boolean active, boolean closed, String workingHours) {
}
