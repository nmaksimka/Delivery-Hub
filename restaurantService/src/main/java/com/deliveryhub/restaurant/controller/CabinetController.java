package com.deliveryhub.restaurant.controller;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.WorkingHoursRequest;
import com.deliveryhub.restaurant.security.OwnerIdentity;
import com.deliveryhub.restaurant.service.RestaurantCabinet;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/cabinet/restaurants")
public class CabinetController {

    private final RestaurantCabinet cabinet;

    public CabinetController(RestaurantCabinet cabinet) {
        this.cabinet = cabinet;
    }

    @GetMapping
    public List<RestaurantDto> myRestaurants(OwnerIdentity owner) {
        return cabinet.restaurantsOf(owner.userId());
    }

    @GetMapping("/{restaurantId}")
    public RestaurantDto myRestaurant(OwnerIdentity owner, @PathVariable Long restaurantId) {
        return cabinet.restaurant(owner.userId(), restaurantId);
    }

    @PutMapping("/{restaurantId}/hours")
    public RestaurantDto changeWorkingHours(OwnerIdentity owner, @PathVariable Long restaurantId,
                                            @Valid @RequestBody WorkingHoursRequest request) {
        return cabinet.changeWorkingHours(owner.userId(), restaurantId, request);
    }

    @PutMapping(value = "/{restaurantId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestaurantDto uploadImage(OwnerIdentity owner, @PathVariable Long restaurantId,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        return cabinet.replaceImage(owner.userId(), restaurantId, file.getBytes());
    }

    @GetMapping("/{restaurantId}/menu")
    public List<MenuItemDto> menu(OwnerIdentity owner, @PathVariable Long restaurantId) {
        return cabinet.menu(owner.userId(), restaurantId);
    }

    @PostMapping("/{restaurantId}/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto addMenuItem(OwnerIdentity owner, @PathVariable Long restaurantId,
                                   @Valid @RequestBody MenuItemRequest request) {
        return cabinet.addMenuItem(owner.userId(), restaurantId, request);
    }

    @PutMapping("/{restaurantId}/menu/{menuItemId}")
    public MenuItemDto updateMenuItem(OwnerIdentity owner, @PathVariable Long restaurantId,
                                      @PathVariable Long menuItemId,
                                      @Valid @RequestBody MenuItemRequest request) {
        return cabinet.updateMenuItem(owner.userId(), restaurantId, menuItemId, request);
    }

    @PutMapping(value = "/{restaurantId}/menu/{menuItemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MenuItemDto uploadMenuItemImage(OwnerIdentity owner, @PathVariable Long restaurantId,
                                           @PathVariable Long menuItemId,
                                           @RequestPart("file") MultipartFile file) throws IOException {
        return cabinet.replaceMenuItemImage(owner.userId(), restaurantId, menuItemId, file.getBytes());
    }

    @DeleteMapping("/{restaurantId}/menu/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMenuItem(OwnerIdentity owner, @PathVariable Long restaurantId,
                               @PathVariable Long menuItemId) {
        cabinet.removeMenuItem(owner.userId(), restaurantId, menuItemId);
    }

    @GetMapping("/{restaurantId}/stop-list")
    public List<MenuItemDto> stopList(OwnerIdentity owner, @PathVariable Long restaurantId) {
        return cabinet.stopList(owner.userId(), restaurantId);
    }

    @PutMapping("/{restaurantId}/stop-list/{menuItemId}")
    public MenuItemDto stopSelling(OwnerIdentity owner, @PathVariable Long restaurantId,
                                   @PathVariable Long menuItemId) {
        return cabinet.stopSelling(owner.userId(), restaurantId, menuItemId);
    }

    @DeleteMapping("/{restaurantId}/stop-list/{menuItemId}")
    public MenuItemDto resumeSelling(OwnerIdentity owner, @PathVariable Long restaurantId,
                                     @PathVariable Long menuItemId) {
        return cabinet.resumeSelling(owner.userId(), restaurantId, menuItemId);
    }
}
