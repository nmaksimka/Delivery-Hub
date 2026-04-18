package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;

    public List<RestaurantDto> getAllRestaurants(boolean activeOnly) {
        List<Restaurant> restaurants = activeOnly ? restaurantRepository.findByActiveTrue()
                : restaurantRepository.findAll();

        return restaurants.stream().map(restaurantMapper::toDto).collect(Collectors.toList());
    }

    public RestaurantDto getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with id: " + id + " was not found"));

        return restaurantMapper.toDto(restaurant);
    }

    @Transactional
    public RestaurantDto createRestaurant(RestaurantDto restaurantDto) {
        Restaurant restaurant = restaurantMapper.toEntity(restaurantDto);
        Restaurant saved = restaurantRepository.save(restaurant);
        return restaurantMapper.toDto(saved);
    }

    @Transactional
    public void removeRestaurant(Long id) {
        if(!restaurantRepository.existsById(id)) {
            throw new EntityNotFoundException("Restaurant with id: " + id + " was not found");
        }
        restaurantRepository.deleteById(id);
    }

    @Transactional
    public RestaurantDto updateRestaurant(Long id, RestaurantDto newRestaurantDto) {
        Restaurant updatedRestaurantIfExists = restaurantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant with Id: " + id + " was not found"));
        restaurantMapper.updateEntityFromDto(newRestaurantDto, updatedRestaurantIfExists);
        Restaurant savedUpdatedRestaurant = restaurantRepository.save(updatedRestaurantIfExists);

        return restaurantMapper.toDto(savedUpdatedRestaurant);
    }
}
