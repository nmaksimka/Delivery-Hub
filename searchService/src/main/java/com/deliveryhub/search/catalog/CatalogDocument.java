package com.deliveryhub.search.catalog;

import com.deliveryhub.contracts.events.CatalogDishPayload;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;

import java.math.BigDecimal;

public record CatalogDocument(
        String kind,
        Long restaurantId,
        Long menuItemId,
        String name,
        String description,
        String category,
        String restaurantName,
        String cuisine,
        String address,
        String section,
        BigDecimal price,
        Boolean available,
        Boolean active,
        long changedAt) {

    public static final String RESTAURANT = "RESTAURANT";
    public static final String DISH = "DISH";
    public static final String REMOVED = "REMOVED";

    public static String restaurantKey(long restaurantId) {
        return "restaurant-" + restaurantId;
    }

    public String key() {
        return DISH.equals(kind) ? "dish-" + menuItemId : restaurantKey(restaurantId);
    }

    public static CatalogDocument restaurantOf(RestaurantCatalogChangedEvent event) {
        return new CatalogDocument(event.getRemoved() ? REMOVED : RESTAURANT, event.getRestaurantId(), null,
                event.getName(), null, null, event.getName(), event.getCuisine(), event.getAddress(), null, null,
                true, event.getActive() && !event.getRemoved(), event.getChangedAt().toEpochMilli());
    }

    public static CatalogDocument dishOf(RestaurantCatalogChangedEvent event, CatalogDishPayload dish) {
        return new CatalogDocument(DISH, event.getRestaurantId(), dish.getMenuItemId(), dish.getName(),
                dish.getDescription(), dish.getCategory(), event.getName(), event.getCuisine(), null,
                dish.getSection(), dish.getPrice(), dish.getAvailable(), event.getActive(),
                event.getChangedAt().toEpochMilli());
    }
}
