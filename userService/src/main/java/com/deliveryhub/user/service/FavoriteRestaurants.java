package com.deliveryhub.user.service;

import com.deliveryhub.user.repository.FavoriteRestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class FavoriteRestaurants {

    private final FavoriteRestaurantRepository favorites;
    private final Clock clock;

    public FavoriteRestaurants(FavoriteRestaurantRepository favorites, Clock clock) {
        this.favorites = favorites;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> list(Long userId) {
        return favorites.findRestaurantIds(userId);
    }

    @Transactional
    public void add(Long userId, Long restaurantId) {
        favorites.addIfAbsent(userId, restaurantId, clock.instant());
    }

    @Transactional
    public void remove(Long userId, Long restaurantId) {
        favorites.remove(userId, restaurantId);
    }
}
