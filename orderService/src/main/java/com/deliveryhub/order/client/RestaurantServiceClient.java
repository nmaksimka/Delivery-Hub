package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.client.dto.MenuItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "restaurantService", url = "${app.restaurantService.url}")
public interface RestaurantServiceClient {
    @GetMapping("/api/restaurants/{id}")
    RestaurantDto getRestaurantById(@PathVariable("id") Long id);

    @GetMapping("/api/restaurants/{restaurantId}/menu/{menuItemId}")
    MenuItemDto getMenuItemById(@PathVariable("restaurantId") Long restaurantId,
                                @PathVariable("menuItemId") Long menuItemId);
}
