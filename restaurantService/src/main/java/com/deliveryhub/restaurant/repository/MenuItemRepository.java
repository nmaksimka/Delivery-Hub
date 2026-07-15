package com.deliveryhub.restaurant.repository;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndAvailableTrue(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndSection(Long restaurantId, MenuSection section);

    List<MenuItem> findByRestaurantIdAndSectionAndAvailableTrue(Long restaurantId, MenuSection section);

    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<MenuItem> findByRestaurantIdAndExternalSourceAndExternalId(Long restaurantId, String externalSource,
                                                                       String externalId);

    List<MenuItem> findByRestaurantIdAndIdIn(Long restaurantId, Collection<Long> ids);
}
