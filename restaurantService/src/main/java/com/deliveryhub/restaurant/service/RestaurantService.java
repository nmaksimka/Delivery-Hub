package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageIntake;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final ImageIntake imageIntake;

    public RestaurantService(RestaurantRepository restaurantRepository, RestaurantMapper restaurantMapper,
                             ImageIntake imageIntake) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantMapper = restaurantMapper;
        this.imageIntake = imageIntake;
    }

    public List<RestaurantDto> getAllRestaurants(boolean activeOnly) {
        List<Restaurant> restaurants = activeOnly
                ? restaurantRepository.findByActiveTrue()
                : restaurantRepository.findAll();
        return restaurants.stream().map(restaurantMapper::toDto).toList();
    }

    public RestaurantDto getRestaurantById(Long id) {
        return restaurantMapper.toDto(findByIdOrThrow(id));
    }

    @Transactional
    public RestaurantDto createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = new Restaurant(request.name(), request.address(), request.phone());
        restaurant.changeWorkingHours(request.openTime(), request.closeTime());
        applyActivity(restaurant, request);
        return restaurantMapper.toDto(restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantDto updateRestaurant(Long id, RestaurantRequest request) {
        Restaurant restaurant = findByIdOrThrow(id);
        restaurant.changeContacts(request.name(), request.address(), request.phone());
        restaurant.changeWorkingHours(request.openTime(), request.closeTime());
        applyActivity(restaurant, request);
        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public RestaurantDto replaceImage(Long id, byte[] content) {
        Restaurant restaurant = findByIdOrThrow(id);
        String key = "restaurants/" + id + "/" + UUID.randomUUID();
        imageIntake.save(key, content);
        imageIntake.discardAfterCommit(restaurant.getImageKey());
        restaurant.storeImage(key);
        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public void removeRestaurant(Long id) {
        restaurantRepository.delete(findByIdOrThrow(id));
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
