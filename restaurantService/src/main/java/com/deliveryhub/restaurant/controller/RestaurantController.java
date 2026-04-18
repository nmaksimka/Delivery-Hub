package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants")
public class RestaurantController {
    private final RestaurantService restaurantService;

    @GetMapping
    public List<RestaurantDto> getAllRestaurants(@RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return restaurantService.getAllRestaurants(activeOnly);
    }

    @GetMapping("/{id}")
    public RestaurantDto getRestaurantById(Long id) {
        return restaurantService.getRestaurantById(id);
    }

    @PostMapping
    @ResponseStatus (HttpStatus.CREATED)
    public RestaurantDto createRestaurant(@Valid @RequestBody RestaurantDto restaurantDto) {
        return restaurantService.createRestaurant(restaurantDto);
    }

    @PutMapping("/{id}")
    public RestaurantDto updateRestaurant(@PathVariable Long id, @Valid @RequestBody RestaurantDto restaurantDto) {
        return restaurantService.updateRestaurant(id, restaurantDto);
    }

    @DeleteMapping("/{id}")
    public void deleteRestaurant(@PathVariable Long id) {
        restaurantService.removeRestaurant(id);
    }
}