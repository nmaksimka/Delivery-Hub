package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageIntake;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemMapper menuItemMapper;
    private final ImageIntake imageIntake;

    public MenuItemService(MenuItemRepository menuItemRepository,
                           RestaurantRepository restaurantRepository,
                           MenuItemMapper menuItemMapper,
                           ImageIntake imageIntake) {
        this.menuItemRepository = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemMapper = menuItemMapper;
        this.imageIntake = imageIntake;
    }

    public List<MenuItemDto> getMenuItemsByRestaurant(Long restaurantId, boolean availableOnly) {
        return getMenuItemsByRestaurant(restaurantId, availableOnly, null);
    }

    public List<MenuItemDto> getMenuItemsByRestaurant(Long restaurantId, boolean availableOnly, MenuSection section) {
        List<MenuItem> menuItems;
        if (section == null) {
            menuItems = availableOnly
                    ? menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId)
                    : menuItemRepository.findByRestaurantId(restaurantId);
        } else {
            menuItems = availableOnly
                    ? menuItemRepository.findByRestaurantIdAndSectionAndAvailableTrue(restaurantId, section)
                    : menuItemRepository.findByRestaurantIdAndSection(restaurantId, section);
        }
        return menuItems.stream().map(menuItemMapper::toDto).toList();
    }

    public List<MenuItemDto> getMenuItemsByIds(Long restaurantId, Collection<Long> menuItemIds) {
        return menuItemRepository.findByRestaurantIdAndIdIn(restaurantId, menuItemIds).stream()
                .map(menuItemMapper::toDto)
                .toList();
    }

    public MenuItemDto getMenuItemById(Long restaurantId, Long menuItemId) {
        return menuItemMapper.toDto(findInRestaurantOrThrow(restaurantId, menuItemId));
    }

    @Transactional
    public MenuItemDto addMenuItemToRestaurant(Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Restaurant with ID: " + restaurantId + " was not found"));

        MenuItem menuItem = restaurant.addMenuItem(
                request.name(), request.description(), request.price(), request.category());
        applyAvailability(menuItem, request);
        return menuItemMapper.toDto(menuItemRepository.save(menuItem));
    }

    @Transactional
    public MenuItemDto replaceImage(Long restaurantId, Long menuItemId, byte[] content) {
        MenuItem menuItem = findInRestaurantOrThrow(restaurantId, menuItemId);
        String key = "menu/" + restaurantId + "/" + menuItemId + "/" + UUID.randomUUID();
        imageIntake.save(key, content);
        imageIntake.discardAfterCommit(menuItem.getImageKey());
        menuItem.storeImage(key);
        return menuItemMapper.toDto(menuItem);
    }

    @Transactional
    public void removeMenuItem(Long restaurantId, Long menuItemId) {
        menuItemRepository.delete(findInRestaurantOrThrow(restaurantId, menuItemId));
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long restaurantId, Long menuItemId, MenuItemRequest request) {
        MenuItem menuItem = findInRestaurantOrThrow(restaurantId, menuItemId);
        menuItem.changeDetails(request.name(), request.description(), request.price(), request.category());
        applyAvailability(menuItem, request);
        return menuItemMapper.toDto(menuItem);
    }

    private void applyAvailability(MenuItem menuItem, MenuItemRequest request) {
        if (request.availableOrDefault()) {
            menuItem.putOnSale();
        } else {
            menuItem.withdrawFromSale();
        }
    }

    private MenuItem findInRestaurantOrThrow(Long restaurantId, Long menuItemId) {
        return menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Menu item with ID: " + menuItemId + " was not found in restaurant " + restaurantId));
    }
}
