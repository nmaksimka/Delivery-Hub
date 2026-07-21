package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.WorkingHoursRequest;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RestaurantCabinet {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;

    public RestaurantCabinet(RestaurantRepository restaurantRepository, RestaurantMapper restaurantMapper,
                             RestaurantService restaurantService, MenuItemService menuItemService) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantMapper = restaurantMapper;
        this.restaurantService = restaurantService;
        this.menuItemService = menuItemService;
    }

    public List<RestaurantDto> restaurantsOf(Long ownerId) {
        return restaurantRepository.findByOwnerIdOrderByName(ownerId).stream().map(restaurantMapper::toDto).toList();
    }

    public RestaurantDto restaurant(Long ownerId, Long restaurantId) {
        return restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .map(restaurantMapper::toDto)
                .orElseThrow(() -> notFound(restaurantId));
    }

    @Transactional
    public RestaurantDto changeWorkingHours(Long ownerId, Long restaurantId, WorkingHoursRequest request) {
        requireOwnership(ownerId, restaurantId);
        return restaurantService.changeWorkingHours(restaurantId, request.openTime(), request.closeTime());
    }

    @Transactional
    public RestaurantDto replaceImage(Long ownerId, Long restaurantId, byte[] content) {
        requireOwnership(ownerId, restaurantId);
        return restaurantService.replaceImage(restaurantId, content);
    }

    public List<MenuItemDto> menu(Long ownerId, Long restaurantId) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.getWholeMenu(restaurantId);
    }

    public List<MenuItemDto> stopList(Long ownerId, Long restaurantId) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.getStopList(restaurantId);
    }

    @Transactional
    public MenuItemDto addMenuItem(Long ownerId, Long restaurantId, MenuItemRequest request) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.addMenuItemToRestaurant(restaurantId, request);
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long ownerId, Long restaurantId, Long menuItemId, MenuItemRequest request) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.updateMenuItem(restaurantId, menuItemId, request);
    }

    @Transactional
    public MenuItemDto replaceMenuItemImage(Long ownerId, Long restaurantId, Long menuItemId, byte[] content) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.replaceImage(restaurantId, menuItemId, content);
    }

    @Transactional
    public void removeMenuItem(Long ownerId, Long restaurantId, Long menuItemId) {
        requireOwnership(ownerId, restaurantId);
        menuItemService.removeMenuItem(restaurantId, menuItemId);
    }

    @Transactional
    public MenuItemDto stopSelling(Long ownerId, Long restaurantId, Long menuItemId) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.withdrawFromSale(restaurantId, menuItemId);
    }

    @Transactional
    public MenuItemDto resumeSelling(Long ownerId, Long restaurantId, Long menuItemId) {
        requireOwnership(ownerId, restaurantId);
        return menuItemService.putOnSale(restaurantId, menuItemId);
    }

    private void requireOwnership(Long ownerId, Long restaurantId) {
        if (!restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)) {
            throw notFound(restaurantId);
        }
    }

    private static EntityNotFoundException notFound(Long restaurantId) {
        return new EntityNotFoundException("Restaurant with ID: " + restaurantId + " was not found");
    }
}
