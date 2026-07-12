package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "restaurantService", fallbackFactory = RestaurantServiceFallbackFactory.class)
public interface RestaurantServiceClient {

    @GetMapping("/api/restaurants/{id}")
    RestaurantDto getRestaurantById(@PathVariable("id") Long id);

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    List<MenuItemDto> getMenuItems(@PathVariable("restaurantId") Long restaurantId,
                                   @RequestParam("ids") Collection<Long> ids);
}
