package com.deliveryhub.user.repository;

import com.deliveryhub.user.entity.FavoriteRestaurant;
import com.deliveryhub.user.entity.FavoriteRestaurantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FavoriteRestaurantRepository extends JpaRepository<FavoriteRestaurant, FavoriteRestaurantId> {

    @Query("select f.restaurantId from FavoriteRestaurant f where f.userId = :userId order by f.createdAt desc, f.restaurantId")
    List<Long> findRestaurantIds(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO favorite_restaurants (user_id, restaurant_id, created_at)
            VALUES (:userId, :restaurantId, :createdAt)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addIfAbsent(@Param("userId") Long userId, @Param("restaurantId") Long restaurantId,
                     @Param("createdAt") Instant createdAt);

    @Modifying
    @Query("delete from FavoriteRestaurant f where f.userId = :userId and f.restaurantId = :restaurantId")
    void remove(@Param("userId") Long userId, @Param("restaurantId") Long restaurantId);
}
