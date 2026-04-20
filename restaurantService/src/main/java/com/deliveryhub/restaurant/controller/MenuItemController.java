package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
public class MenuItemController {
    private final MenuItemService menuItemService;

    @GetMapping
    public List<MenuItemDto> getMenuByRestaurant(@PathVariable Long restaurantId,
                                                 @RequestParam(value = "availableOnly", defaultValue = "true") boolean availableOnly) {
        return menuItemService.getMenuItemsByRestaurant(restaurantId, availableOnly);
    }

    @GetMapping("/{menuItemId}")
    public MenuItemDto getMenuItemById(@PathVariable Long menuItemId) {
        return menuItemService.getMenuItemById(menuItemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto addMenuItemToRestaurant(@PathVariable Long restaurantId,
                                               @Valid @RequestBody MenuItemDto menuItemDto) {
        return menuItemService.addMenuItemToRestaurant(restaurantId, menuItemDto);
    }

    @PutMapping("/{menuItemId}")
    public MenuItemDto updateMenuItem(@PathVariable Long menuItemId, @Valid @RequestBody MenuItemDto menuItemDto) {
        return menuItemService.updateMenuItem(menuItemId, menuItemDto);
    }

    @DeleteMapping("/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenuItem(@PathVariable Long menuItemId) {
        menuItemService.removeMenuItem(menuItemId);
    }
}
