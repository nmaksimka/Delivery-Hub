package com.deliveryhub.restaurant.repository;

import com.deliveryhub.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByActiveTrue();

    List<Restaurant> findByOwnerIdOrderByName(Long ownerId);

    Optional<Restaurant> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    Optional<Restaurant> findByExternalSourceAndExternalId(String externalSource, String externalId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Restaurant r SET r.ratingSum = r.ratingSum + :rating, r.ratingCount = r.ratingCount + 1 WHERE r.id = :id")
    int addRating(@Param("id") Long id, @Param("rating") int rating);
}
