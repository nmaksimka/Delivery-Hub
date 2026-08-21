package com.deliveryhub.user.entity;

import java.io.Serializable;

public record FavoriteRestaurantId(Long userId, Long restaurantId) implements Serializable {
}
