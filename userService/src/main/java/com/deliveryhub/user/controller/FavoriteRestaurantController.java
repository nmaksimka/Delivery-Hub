package com.deliveryhub.user.controller;

import com.deliveryhub.user.service.FavoriteRestaurants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/favorites")
public class FavoriteRestaurantController {

    private static final String USER_ID = "X-User-Id";

    private final FavoriteRestaurants favorites;

    public FavoriteRestaurantController(FavoriteRestaurants favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    public List<Long> list(@RequestHeader(USER_ID) Long userId) {
        return favorites.list(userId);
    }

    @PutMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestHeader(USER_ID) Long userId, @PathVariable Long restaurantId) {
        favorites.add(userId, restaurantId);
    }

    @DeleteMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader(USER_ID) Long userId, @PathVariable Long restaurantId) {
        favorites.remove(userId, restaurantId);
    }
}
