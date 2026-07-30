package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.AssignOwnerRequest;
import com.deliveryhub.restaurant.dto.RestaurantOwnerDto;
import com.deliveryhub.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/owner")
public class RestaurantOwnerController {

    private final RestaurantService restaurantService;

    public RestaurantOwnerController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public RestaurantOwnerDto getOwner(@PathVariable Long restaurantId) {
        return restaurantService.getOwner(restaurantId);
    }

    @PutMapping
    public RestaurantOwnerDto assignOwner(@PathVariable Long restaurantId,
                                          @Valid @RequestBody AssignOwnerRequest request) {
        return restaurantService.assignOwner(restaurantId, request.ownerId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOwner(@PathVariable Long restaurantId) {
        restaurantService.removeOwner(restaurantId);
    }
}
