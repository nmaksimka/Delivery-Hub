package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;

import java.util.Map;
import java.util.Optional;

public record MenuQuote(RestaurantDto restaurant, Map<Long, MenuItemDto> items) {

    public Optional<MenuItemDto> item(Long menuItemId) {
        return Optional.ofNullable(items.get(menuItemId));
    }
}
