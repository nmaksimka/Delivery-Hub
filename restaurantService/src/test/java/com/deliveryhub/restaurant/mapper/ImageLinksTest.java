package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.MenuItemView;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.storage.InMemoryImageStorage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ImageLinksTest {

    private final ImageLinks links = new ImageLinks(new InMemoryImageStorage());

    @Test
    void menuItemWithStoredImageGetsSignedUrl() {
        MenuItem item = menuItem();
        item.changeImage("https://external.example/a.jpg");
        item.storeImage("menu/1/2/abc");

        assertThat(links.of(item)).isEqualTo("http://storage.test/images/menu/1/2/abc?signed");
    }

    @Test
    void menuItemWithoutStoredImageKeepsExternalUrl() {
        MenuItem item = menuItem();
        item.changeImage("https://external.example/a.jpg");

        assertThat(links.of(item)).isEqualTo("https://external.example/a.jpg");
    }

    @Test
    void menuItemWithoutAnyImageHasNoUrl() {
        assertThat(links.of(menuItem())).isNull();
    }

    @Test
    void restaurantWithStoredImageGetsSignedUrl() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        restaurant.describe("Italian", "Tomsk", "https://external.example/r.jpg");
        restaurant.storeImage("restaurants/1/abc");

        assertThat(links.of(restaurant)).isEqualTo("http://storage.test/images/restaurants/1/abc?signed");
    }

    @Test
    void restaurantWithoutStoredImageKeepsExternalUrl() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        restaurant.describe("Italian", "Tomsk", "https://external.example/r.jpg");

        assertThat(links.of(restaurant)).isEqualTo("https://external.example/r.jpg");
    }

    @Test
    void cachedRestaurantViewGetsFreshSignedUrlFromKey() {
        RestaurantView view = new RestaurantView(1L, "Pizzeria", null, null, null, null,
                "https://external.example/r.jpg", "restaurants/1/abc", null, null, true, null, null, null, 0);

        assertThat(links.of(view)).isEqualTo("http://storage.test/images/restaurants/1/abc?signed");
    }

    @Test
    void cachedRestaurantViewWithoutKeyKeepsExternalUrl() {
        RestaurantView view = new RestaurantView(1L, "Pizzeria", null, null, null, null,
                "https://external.example/r.jpg", null, null, null, true, null, null, null, 0);

        assertThat(links.of(view)).isEqualTo("https://external.example/r.jpg");
    }

    @Test
    void cachedMenuItemViewGetsSignedUrlFromKey() {
        MenuItemView view = new MenuItemView(2L, "Pizza", null, new BigDecimal("10.00"), "pizza", true,
                null, "menu/1/2/abc", null, null, 1L);

        assertThat(links.of(view)).isEqualTo("http://storage.test/images/menu/1/2/abc?signed");
    }

    private static MenuItem menuItem() {
        return new Restaurant("Pizzeria", null, null).addMenuItem("Pizza", null, new BigDecimal("10.00"), "pizza");
    }
}
