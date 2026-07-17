package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemView;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class CatalogCache {

    public static final String RESTAURANTS = "catalog-restaurants";
    public static final String RESTAURANT = "catalog-restaurant";
    public static final String MENU = "catalog-menu";

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantMapper restaurantMapper;
    private final MenuItemMapper menuItemMapper;

    public CatalogCache(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository,
                        RestaurantMapper restaurantMapper, MenuItemMapper menuItemMapper) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantMapper = restaurantMapper;
        this.menuItemMapper = menuItemMapper;
    }

    @Cacheable(cacheNames = RESTAURANTS, key = "#activeOnly")
    public List<RestaurantView> restaurants(boolean activeOnly) {
        return (activeOnly ? restaurantRepository.findByActiveTrue() : restaurantRepository.findAll()).stream()
                .map(restaurantMapper::toView)
                .toList();
    }

    @Cacheable(cacheNames = RESTAURANT, key = "#id")
    public RestaurantView restaurant(Long id) {
        return restaurantRepository.findById(id)
                .map(restaurantMapper::toView)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with ID: " + id + " was not found"));
    }

    @Cacheable(cacheNames = MENU,
            key = "#restaurantId + ':' + #availableOnly + ':' + (#section == null ? 'all' : #section.name())")
    public List<MenuItemView> menu(Long restaurantId, boolean availableOnly, MenuSection section) {
        return findMenu(restaurantId, availableOnly, section).stream().map(menuItemMapper::toView).toList();
    }

    @CacheEvict(cacheNames = {RESTAURANTS, RESTAURANT}, allEntries = true)
    public void evictRestaurants() {
    }

    @CacheEvict(cacheNames = MENU, allEntries = true)
    public void evictMenus() {
    }

    @CacheEvict(cacheNames = {RESTAURANTS, RESTAURANT, MENU}, allEntries = true)
    public void evictAll() {
    }

    private List<MenuItem> findMenu(Long restaurantId, boolean availableOnly, MenuSection section) {
        if (section == null) {
            return availableOnly
                    ? menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId)
                    : menuItemRepository.findByRestaurantId(restaurantId);
        }
        return availableOnly
                ? menuItemRepository.findByRestaurantIdAndSectionAndAvailableTrue(restaurantId, section)
                : menuItemRepository.findByRestaurantIdAndSection(restaurantId, section);
    }
}
