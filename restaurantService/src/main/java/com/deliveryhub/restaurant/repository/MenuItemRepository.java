package com.deliveryhub.restaurant.repository;

import com.deliveryhub.restaurant.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndAvailableTrue(Long restaurantId);

    /** Блюдо всегда ищем в контексте ресторана, иначе можно достать чужую позицию по её id. */
    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);
}
