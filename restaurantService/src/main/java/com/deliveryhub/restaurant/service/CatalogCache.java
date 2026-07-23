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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
public class CatalogCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);

    public static final String RESTAURANTS = "catalog-restaurants";
    public static final String RESTAURANT = "catalog-restaurant";
    public static final String MENU = "catalog-menu";

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantMapper restaurantMapper;
    private final MenuItemMapper menuItemMapper;
    private final CacheManager cacheManager;

    public CatalogCache(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository,
                        RestaurantMapper restaurantMapper, MenuItemMapper menuItemMapper,
                        CacheManager cacheManager) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.restaurantMapper = restaurantMapper;
        this.menuItemMapper = menuItemMapper;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RESTAURANTS, key = "#activeOnly")
    public List<RestaurantView> restaurants(boolean activeOnly) {
        return (activeOnly ? restaurantRepository.findByActiveTrue() : restaurantRepository.findAll()).stream()
                .map(restaurantMapper::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RESTAURANT, key = "#id")
    public RestaurantView restaurant(Long id) {
        return restaurantRepository.findById(id)
                .map(restaurantMapper::toView)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with ID: " + id + " was not found"));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MENU,
            key = "#restaurantId + ':' + #availableOnly + ':' + (#section == null ? 'all' : #section.name())")
    public List<MenuItemView> menu(Long restaurantId, boolean availableOnly, MenuSection section) {
        return findMenu(restaurantId, availableOnly, section).stream().map(menuItemMapper::toView).toList();
    }

    public void evictRestaurants() {
        evictAfterCommit(RESTAURANTS, RESTAURANT);
    }

    public void evictMenus() {
        evictAfterCommit(MENU);
    }

    public void evictAll() {
        evictAfterCommit(RESTAURANTS, RESTAURANT, MENU);
    }

    private void evictAfterCommit(String... cacheNames) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear(cacheNames);
                }
            });
        } else {
            clear(cacheNames);
        }
    }

    private void clear(String... cacheNames) {
        for (String name : cacheNames) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.invalidate();
                }
            } catch (RuntimeException e) {
                log.warn("Cache {} could not be cleared, entries will expire by ttl: {}", name, e.toString());
            }
        }
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
