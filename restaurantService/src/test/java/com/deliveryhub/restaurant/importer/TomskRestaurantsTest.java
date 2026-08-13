package com.deliveryhub.restaurant.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TomskRestaurantsTest {

    @Test
    void knownCuisineGetsRussianNameAndLabel() {
        TomskRestaurants.Profile italian = TomskRestaurants.forArea("Italian");

        assertThat(italian.name()).isEqualTo("Траттория «Белла Виста»");
        assertThat(italian.cuisine()).isEqualTo("Итальянская");
    }

    @Test
    void unknownCuisinesShareOneRestaurant() {
        assertThat(TomskRestaurants.areaKey("Martian")).isEqualTo("World");
        assertThat(TomskRestaurants.areaKey(null)).isEqualTo("World");
        assertThat(TomskRestaurants.forArea("Martian").name()).isEqualTo("Кухни мира");
    }

    @Test
    void addressIsInTomskAndStable() {
        String address = TomskRestaurants.addressFor("Japanese");

        assertThat(address).startsWith("г. Томск, ").isEqualTo(TomskRestaurants.addressFor("Japanese"));
        assertThat(TomskRestaurants.phoneFor("Japanese")).matches("\\+73822\\d{6}");
    }

    @Test
    void translatesCategories() {
        assertThat(TomskRestaurants.categoryName("Seafood")).isEqualTo("Морепродукты");
        assertThat(TomskRestaurants.categoryName("Fusion")).isEqualTo("Fusion");
        assertThat(TomskRestaurants.categoryName(null)).isEqualTo("Разное");
    }

    @Test
    void locationIsInsideTomskAndStable() {
        TomskRestaurants.Location location = TomskRestaurants.locationFor("Japanese");

        assertThat(location).isEqualTo(TomskRestaurants.locationFor("Japanese"));
        assertThat(location.latitude()).isBetween(56.45, 56.51);
        assertThat(location.longitude()).isBetween(84.93, 85.03);
    }
}
