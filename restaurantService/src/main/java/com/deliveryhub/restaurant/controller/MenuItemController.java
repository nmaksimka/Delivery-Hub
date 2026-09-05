package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
public class MenuItemController {
    private final MenuItemService menuItemService;

    @GetMapping
    public List<MenuItemDto> getMenuByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(value = "availableOnly", defaultValue = "true") boolean availableOnly) {
        return menuItemService.getMenuItemsByRestaurant(restaurantId, availableOnly);
    }

    @GetMapping("/{menuItemId}")
    public MenuItemDto getMenuItemById(@PathVariable Long restaurantId, @PathVariable Long menuItemId) {
        return menuItemService.getMenuItemById(restaurantId, menuItemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto addMenuItemToRestaurant(@PathVariable Long restaurantId,
                                               @Valid @RequestBody MenuItemDto menuItemDto) {
        return menuItemService.addMenuItemToRestaurant(restaurantId, menuItemDto);
    }

    @PutMapping("/{menuItemId}")
    public MenuItemDto updateMenuItem(@PathVariable Long restaurantId,
                                      @PathVariable Long menuItemId,
                                      @Valid @RequestBody MenuItemDto menuItemDto) {
        return menuItemService.updateMenuItem(restaurantId, menuItemId, menuItemDto);
    }

    @DeleteMapping("/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenuItem(@PathVariable Long restaurantId, @PathVariable Long menuItemId) {
        menuItemService.removeMenuItem(restaurantId, menuItemId);
    }
}
