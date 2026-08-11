package com.deliveryhub.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "favorite_restaurants")
@IdClass(FavoriteRestaurantId.class)
public class FavoriteRestaurant {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Id
    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FavoriteRestaurant() {
    }

    public FavoriteRestaurant(Long userId, Long restaurantId, Instant createdAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
