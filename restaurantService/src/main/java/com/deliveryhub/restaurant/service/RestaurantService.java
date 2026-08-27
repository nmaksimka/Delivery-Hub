package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantOwnerDto;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.outbox.CatalogChanges;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageIntake;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final ImageIntake imageIntake;
    private final CatalogCache catalogCache;
    private final CatalogChanges catalogChanges;

    public RestaurantService(RestaurantRepository restaurantRepository, RestaurantMapper restaurantMapper,
                             ImageIntake imageIntake, CatalogCache catalogCache,
                             CatalogChanges catalogChanges) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantMapper = restaurantMapper;
        this.imageIntake = imageIntake;
        this.catalogCache = catalogCache;
        this.catalogChanges = catalogChanges;
    }

    public List<RestaurantDto> getAllRestaurants(boolean activeOnly) {
        return catalogCache.restaurants(activeOnly).stream().map(restaurantMapper::fromView).toList();
    }

    public RestaurantDto getRestaurantById(Long id) {
        return restaurantMapper.fromView(catalogCache.restaurant(id));
    }

    @Transactional
    public RestaurantDto createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = new Restaurant(request.name(), request.address(), request.phone());
        restaurant.changeWorkingHours(request.openTime(), request.closeTime());
        if (request.latitude() != null || request.longitude() != null) {
            restaurant.locate(request.latitude(), request.longitude());
        }
        applyActivity(restaurant, request);
        Restaurant saved = restaurantRepository.save(restaurant);
        catalogChanges.restaurantChanged(saved.getId());
        RestaurantDto created = restaurantMapper.toDto(saved);
        catalogCache.evictRestaurants();
        return created;
    }

    @Transactional
    public RestaurantDto updateRestaurant(Long id, RestaurantRequest request) {
        Restaurant restaurant = findByIdOrThrow(id);
        restaurant.changeContacts(request.name(), request.address(), request.phone());
        restaurant.changeWorkingHours(request.openTime(), request.closeTime());
        if (request.latitude() != null || request.longitude() != null) {
            restaurant.locate(request.latitude(), request.longitude());
        }
        applyActivity(restaurant, request);
        catalogChanges.restaurantChanged(id);
        catalogCache.evictRestaurants();
        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public RestaurantDto changeWorkingHours(Long id, LocalTime openTime, LocalTime closeTime) {
        Restaurant restaurant = findByIdOrThrow(id);
        restaurant.changeWorkingHours(openTime, closeTime);
        catalogChanges.restaurantChanged(id);
        catalogCache.evictRestaurants();
        return restaurantMapper.toDto(restaurant);
    }

    public RestaurantOwnerDto getOwner(Long id) {
        Restaurant restaurant = findByIdOrThrow(id);
        return new RestaurantOwnerDto(restaurant.getId(), restaurant.getOwnerId());
    }

    @Transactional
    public RestaurantOwnerDto assignOwner(Long id, Long ownerId) {
        Restaurant restaurant = findByIdOrThrow(id);
        restaurant.assignOwner(ownerId);
        log.info("Restaurant {} is now owned by user {}", id, ownerId);
        return new RestaurantOwnerDto(restaurant.getId(), restaurant.getOwnerId());
    }

    @Transactional
    public void removeOwner(Long id) {
        findByIdOrThrow(id).removeOwner();
        log.info("Restaurant {} has no owner now", id);
    }

    @Transactional
    public RestaurantDto replaceImage(Long id, byte[] content) {
        Restaurant restaurant = findByIdOrThrow(id);
        String key = "restaurants/" + id + "/" + UUID.randomUUID();
        imageIntake.save(key, content);
        imageIntake.discardAfterCommit(restaurant.getImageKey());
        restaurant.storeImage(key);
        catalogCache.evictRestaurants();
        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public void removeRestaurant(Long id) {
        Restaurant restaurant = findByIdOrThrow(id);
        imageIntake.discardAfterCommit(restaurant.getImageKey());
        restaurant.getMenuItems().forEach(item -> imageIntake.discardAfterCommit(item.getImageKey()));
        restaurantRepository.delete(restaurant);
        catalogChanges.restaurantRemoved(id, restaurant.getName());
        catalogCache.evictAll();
    }

    private void applyActivity(Restaurant restaurant, RestaurantRequest request) {
        if (request.activeOrDefault()) {
            restaurant.activate();
        } else {
            restaurant.deactivate();
        }
    }

    private Restaurant findByIdOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with ID: " + id + " was not found"));
    }
}
