package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {
    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

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
    public RestaurantDto createRestaurant(@Valid @RequestBody RestaurantRequest request) {
        return restaurantService.createRestaurant(request);
    }

    @PutMapping("/{id}")
    public RestaurantDto updateRestaurant(@PathVariable Long id,
                                          @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.updateRestaurant(id, request);
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestaurantDto uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file)
            throws IOException {
        return restaurantService.replaceImage(id, file.getBytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRestaurant(@PathVariable Long id) {
        restaurantService.removeRestaurant(id);
    }
}
