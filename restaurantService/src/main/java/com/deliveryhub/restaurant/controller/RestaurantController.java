package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {
    private final RestaurantService restaurantService;

    @GetMapping
    public List<RestaurantDto> getAllRestaurants(
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return restaurantService.getAllRestaurants(activeOnly);
    }

    @GetMapping("/{id}")
    public RestaurantDto getRestaurantById(@PathVariable Long id) {
        return restaurantService.getRestaurantById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantDto createRestaurant(@Valid @RequestBody RestaurantDto restaurantDto) {
        return restaurantService.createRestaurant(restaurantDto);
    }

    @PutMapping("/{id}")
    public RestaurantDto updateRestaurant(@PathVariable Long id,
                                          @Valid @RequestBody RestaurantDto restaurantDto) {
        return restaurantService.updateRestaurant(id, restaurantDto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRestaurant(@PathVariable Long id) {
        restaurantService.removeRestaurant(id);
    }
}
