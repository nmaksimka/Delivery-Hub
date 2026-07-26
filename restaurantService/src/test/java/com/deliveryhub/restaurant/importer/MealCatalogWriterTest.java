package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mealdb.MealDbMeal;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MealCatalogWriterTest {

    private static final UUID JOB = UUID.randomUUID();
    private static final MealDbMeal MEAL = new MealDbMeal("52772", "Teriyaki Chicken", "Chicken", "Japanese",
            "Cook it.", "https://www.themealdb.com/images/52772.jpg");

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
    private final ImportProgress progress = mock(ImportProgress.class);
    private final MealCatalogWriter writer = new MealCatalogWriter(restaurantRepository, menuItemRepository, progress);

    @Test
    void leavesRestaurantWithOwnerUntouched() {
        Restaurant owned = Restaurant.imported("MEALDB", "Japanese", "Сакура", "ул. Ленина, 1", null);
        owned.assignOwner(7L);
        MenuItem edited = owned.addMenuItem("Своё название", "Своё описание", new BigDecimal("999.00"), "Горячее");
        when(restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Japanese"))
                .thenReturn(Optional.of(owned));

        writer.store(JOB, MEAL, "mealdb/meals/52772");

        assertThat(edited.getName()).isEqualTo("Своё название");
        assertThat(edited.getPrice()).isEqualByComparingTo("999.00");
        assertThat(owned.getImageUrl()).isNull();
        assertThat(owned.getImageKey()).isNull();
        verifyNoInteractions(menuItemRepository);
        verify(progress).change(eq(JOB), any());
    }

    @Test
    void keepsUploadedImageOfExistingItem() {
        Restaurant restaurant = Restaurant.imported("MEALDB", "Japanese", "Сакура", "ул. Ленина, 1", null);
        MenuItem item = restaurant.addMenuItem("Teriyaki Chicken", null, new BigDecimal("450.00"), "Горячее");
        item.storeImage("menu/1/2/uploaded");
        when(restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Japanese"))
                .thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndExternalSourceAndExternalId(any(), eq("MEALDB"), anyString()))
                .thenReturn(Optional.of(item));

        writer.store(JOB, MEAL, "mealdb/meals/52772");

        assertThat(item.getImageKey()).isEqualTo("menu/1/2/uploaded");
    }

    @Test
    void firstImportedImageOfRestaurantStays() {
        Restaurant restaurant = Restaurant.imported("MEALDB", "Japanese", "Сакура", "ул. Ленина, 1", null);
        restaurant.useImportedImage("https://www.themealdb.com/images/first.jpg");
        when(restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Japanese"))
                .thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndExternalSourceAndExternalId(any(), eq("MEALDB"), anyString()))
                .thenReturn(Optional.empty());

        writer.store(JOB, MEAL, null);

        assertThat(restaurant.getImageUrl()).isEqualTo("https://www.themealdb.com/images/first.jpg");
    }
}
