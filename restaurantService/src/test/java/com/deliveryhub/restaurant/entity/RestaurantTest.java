package com.deliveryhub.restaurant.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantTest {

    @Test
    void newRestaurantIsActive() {
        assertThat(new Restaurant("Pizzeria", "Lenina 1", "+79990000000").isActive()).isTrue();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Restaurant(" ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addedMenuItemBelongsToRestaurantAndIsOnSale() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        MenuItem item = restaurant.addMenuItem("Margherita", "classic", new BigDecimal("450.00"), "pizza");

        assertThat(item.getRestaurant()).isSameAs(restaurant);
        assertThat(item.isAvailable()).isTrue();
        assertThat(restaurant.getMenuItems()).containsExactly(item);
    }

    @Test
    void rejectsMenuItemWithNonPositivePrice() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        assertThatThrownBy(() -> restaurant.addMenuItem("Margherita", null, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
        assertThat(restaurant.getMenuItems()).isEmpty();
    }

    @Test
    void menuItemsCannotBeChangedFromOutside() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        assertThatThrownBy(() -> restaurant.getMenuItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withdrawnItemIsNotAvailable() {
        MenuItem item = new Restaurant("Pizzeria", null, null)
                .addMenuItem("Cola", null, new BigDecimal("120.00"), "drinks");

        item.withdrawFromSale();

        assertThat(item.isAvailable()).isFalse();
    }
}
