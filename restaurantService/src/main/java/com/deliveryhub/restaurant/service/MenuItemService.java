package com.deliveryhub.restaurant.service;


import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemMapper menuItemMapper;

    public List<MenuItemDto> getMenuItemsByRestaurant(Long restaurantId, boolean availableOnly) {
        List<MenuItem> menuItems = availableOnly ? menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId)
                : menuItemRepository.findByRestaurantId(restaurantId);

        return menuItems.stream().map(menuItemMapper::toDto).collect(Collectors.toList());
    }

    public MenuItemDto getMenuItemById(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem with this id: " + id + " was not found"));

        return menuItemMapper.toDto(item);
    }

    @Transactional
    public MenuItemDto addMenuItemToRestaurant(Long restaurantId, MenuItemDto menuItemDto) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with Id: " + restaurantId + " was not found"));
        MenuItem menuItem = menuItemMapper.toEntity(menuItemDto);

        restaurant.addMenuItem(menuItem);

        MenuItem saved = menuItemRepository.save(menuItem);
        return menuItemMapper.toDto(saved);
    }

    @Transactional
    public void removeMenuItem(Long id) {
        if(!menuItemRepository.existsById(id)) {
            throw new EntityNotFoundException("MenuItem with this id: " + id + " was not found");
        }
        menuItemRepository.deleteById(id);
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long id, MenuItemDto newItemDto) {
        MenuItem oldMenuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem with this id: " + id + " was not found"));

        menuItemMapper.updateEntityFromDto(newItemDto, oldMenuItem);
        MenuItem updatedMenuItem = menuItemRepository.save(oldMenuItem);

        return menuItemMapper.toDto(updatedMenuItem);
    }
}
