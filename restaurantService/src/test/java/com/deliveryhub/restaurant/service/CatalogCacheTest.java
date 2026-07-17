package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogCacheTest {

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache restaurants = mock(Cache.class);
    private final Cache restaurant = mock(Cache.class);
    private final Cache menu = mock(Cache.class);

    private final CatalogCache catalogCache = new CatalogCache(restaurantRepository, mock(MenuItemRepository.class),
            mock(RestaurantMapper.class), mock(MenuItemMapper.class), cacheManager);

    @BeforeEach
    void wireCaches() {
        when(cacheManager.getCache(CatalogCache.RESTAURANTS)).thenReturn(restaurants);
        when(cacheManager.getCache(CatalogCache.RESTAURANT)).thenReturn(restaurant);
        when(cacheManager.getCache(CatalogCache.MENU)).thenReturn(menu);
    }

    @AfterEach
    void leaveNoSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsRestaurantCachesImmediatelyOutsideTransaction() {
        catalogCache.evictRestaurants();

        verify(restaurants).invalidate();
        verify(restaurant).invalidate();
        verify(menu, never()).invalidate();
    }

    @Test
    void evictsMenuCacheOnly() {
        catalogCache.evictMenus();

        verify(menu).invalidate();
        verify(restaurants, never()).invalidate();
        verify(restaurant, never()).invalidate();
    }

    @Test
    void evictsEverythingAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        catalogCache.evictAll();

        verify(restaurants, never()).invalidate();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(restaurants).invalidate();
        verify(restaurant).invalidate();
        verify(menu).invalidate();
    }

    @Test
    void doesNotEvictWhenTransactionIsRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        catalogCache.evictRestaurants();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(restaurants, never()).invalidate();
    }

    @Test
    void survivesUnavailableRedisWhenEvicting() {
        when(restaurants.invalidate()).thenThrow(new IllegalStateException("redis is down"));

        catalogCache.evictRestaurants();

        verify(restaurant).invalidate();
    }

    @Test
    void missingRestaurantIsNotCached() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogCache.restaurant(404L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("404");
    }
}
