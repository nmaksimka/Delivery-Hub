package com.deliveryhub.restaurant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;

    public RestaurantService(RestaurantRepository restaurantRepository, RestaurantMapper restaurantMapper) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantMapper = restaurantMapper;
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
    public RestaurantDto createRestaurant(RestaurantDto restaurantDto) {
        Restaurant restaurant = restaurantMapper.toEntity(restaurantDto);
        return restaurantMapper.toDto(restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantDto updateRestaurant(Long id, RestaurantDto newRestaurantDto) {
        Restaurant restaurant = findByIdOrThrow(id);
        restaurantMapper.updateEntityFromDto(newRestaurantDto, restaurant);
        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public void removeRestaurant(Long id) {
        restaurantRepository.delete(findByIdOrThrow(id));
    }

    private Restaurant findByIdOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with ID: " + id + " was not found"));
    }
}
