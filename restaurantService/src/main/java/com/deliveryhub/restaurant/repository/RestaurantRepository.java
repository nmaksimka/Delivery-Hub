package com.deliveryhub.restaurant.repository;

import com.deliveryhub.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByActiveTrue();

    Optional<Restaurant> findByExternalSourceAndExternalId(String externalSource, String externalId);
}
