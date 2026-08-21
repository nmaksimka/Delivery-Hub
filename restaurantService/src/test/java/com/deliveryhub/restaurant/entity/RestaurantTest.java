package com.deliveryhub.restaurant.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantTest {

    @Test
    void newRestaurantIsActive() {
        assertThat(new Restaurant("Pizzeria", "Lenina 1", "+79990000000").isActive()).isTrue();
    }

    @Test
    void isOpenOnlyBetweenOpeningAndClosing() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        restaurant.changeWorkingHours(LocalTime.of(10, 0), LocalTime.of(22, 0));

        assertThat(restaurant.isOpenAt(LocalTime.of(9, 59))).isFalse();
        assertThat(restaurant.isOpenAt(LocalTime.of(10, 0))).isTrue();
        assertThat(restaurant.isOpenAt(LocalTime.of(21, 59))).isTrue();
        assertThat(restaurant.isOpenAt(LocalTime.of(22, 0))).isFalse();
    }

    @Test
    void worksOverMidnightWhenClosingIsEarlierThanOpening() {
        Restaurant restaurant = new Restaurant("Night bar", null, null);
        restaurant.changeWorkingHours(LocalTime.of(18, 0), LocalTime.of(2, 0));

        assertThat(restaurant.isOpenAt(LocalTime.of(23, 30))).isTrue();
        assertThat(restaurant.isOpenAt(LocalTime.of(1, 59))).isTrue();
        assertThat(restaurant.isOpenAt(LocalTime.of(2, 0))).isFalse();
        assertThat(restaurant.isOpenAt(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void isAlwaysOpenWithoutHoursOrWithEqualOpeningAndClosing() {
        Restaurant withoutHours = new Restaurant("Pizzeria", null, null);
        Restaurant roundTheClock = new Restaurant("Shawarma 24", null, null);
        roundTheClock.changeWorkingHours(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

        assertThat(withoutHours.isOpenAt(LocalTime.of(3, 0))).isTrue();
        assertThat(roundTheClock.isOpenAt(LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void belongsOnlyToAssignedOwner() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        assertThat(restaurant.isOwnedBy(7L)).isFalse();

        restaurant.assignOwner(7L);
        assertThat(restaurant.isOwnedBy(7L)).isTrue();
        assertThat(restaurant.isOwnedBy(8L)).isFalse();

        restaurant.removeOwner();
        assertThat(restaurant.isOwnedBy(7L)).isFalse();
        assertThat(restaurant.isOwnedBy(null)).isFalse();
    }

    @Test
    void rejectsNonPositiveOwnerId() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        assertThatThrownBy(() -> restaurant.assignOwner(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restaurant.assignOwner(null)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void locationNeedsBothCoordinates() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);

        assertThatThrownBy(() -> restaurant.locate(56.48, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restaurant.locate(91.0, 84.9)).isInstanceOf(IllegalArgumentException.class);

        restaurant.locate(56.48, 84.95);
        assertThat(restaurant.hasLocation()).isTrue();
        assertThat(restaurant.getLongitude()).isEqualTo(84.95);
    }
}
