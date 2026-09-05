package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemMapper menuItemMapper;

    public List<MenuItemDto> getMenuItemsByRestaurant(Long restaurantId, boolean availableOnly) {
        List<MenuItem> menuItems = availableOnly
                ? menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId)
                : menuItemRepository.findByRestaurantId(restaurantId);

        return menuItems.stream().map(menuItemMapper::toDto).toList();
    }

    public MenuItemDto getMenuItemById(Long restaurantId, Long menuItemId) {
        return menuItemMapper.toDto(findInRestaurantOrThrow(restaurantId, menuItemId));
    }

    @Transactional
    public MenuItemDto addMenuItemToRestaurant(Long restaurantId, MenuItemDto menuItemDto) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Restaurant with ID: " + restaurantId + " was not found"));

        MenuItem menuItem = menuItemMapper.toEntity(menuItemDto);
        restaurant.addMenuItem(menuItem);

        return menuItemMapper.toDto(menuItemRepository.save(menuItem));
    }

    @Transactional
    public void removeMenuItem(Long restaurantId, Long menuItemId) {
        menuItemRepository.delete(findInRestaurantOrThrow(restaurantId, menuItemId));
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long restaurantId, Long menuItemId, MenuItemDto newItemDto) {
        MenuItem menuItem = findInRestaurantOrThrow(restaurantId, menuItemId);
        menuItemMapper.updateEntityFromDto(newItemDto, menuItem);
        return menuItemMapper.toDto(menuItem);
    }

    private MenuItem findInRestaurantOrThrow(Long restaurantId, Long menuItemId) {
        return menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Menu item with ID: " + menuItemId + " was not found in restaurant " + restaurantId));
    }
}
