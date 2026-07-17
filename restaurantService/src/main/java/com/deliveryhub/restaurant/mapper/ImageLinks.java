package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.MenuItemView;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.storage.ImageStorage;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class ImageLinks {

    private final ImageStorage imageStorage;

    public ImageLinks(ImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    @Named("menuItemImage")
    public String of(MenuItem menuItem) {
        return resolve(menuItem.getImageKey(), menuItem.getImageUrl());
    }

    @Named("restaurantImage")
    public String of(Restaurant restaurant) {
        return resolve(restaurant.getImageKey(), restaurant.getImageUrl());
    }

    @Named("menuItemViewImage")
    public String of(MenuItemView view) {
        return resolve(view.imageKey(), view.externalImageUrl());
    }

    @Named("restaurantViewImage")
    public String of(RestaurantView view) {
        return resolve(view.imageKey(), view.externalImageUrl());
    }

    private String resolve(String imageKey, String externalUrl) {
        return imageKey == null ? externalUrl : imageStorage.readUrl(imageKey);
    }
}
