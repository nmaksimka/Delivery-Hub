package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mealdb.MealDbMeal;
import com.deliveryhub.restaurant.outbox.CatalogChanges;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageSources;
import com.deliveryhub.restaurant.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
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
    private final MealCatalogWriter writer = new MealCatalogWriter(restaurantRepository, menuItemRepository, progress,
            new ImageSources(new StorageProperties("http://localhost:1", "http://localhost:1", "garage",
                    "images", "key", "secret", Duration.ofMinutes(10), DataSize.ofMegabytes(1), Duration.ofSeconds(1),
                    Duration.ofSeconds(2), Duration.ofSeconds(30), new StorageProperties.Mirror(Set.of("https"), Set.of("www.themealdb.com"))),
                    host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")}),
            mock(CatalogChanges.class));

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

    @Test
    void dropsImageUrlFromUntrustedSource() {
        MealDbMeal tracked = new MealDbMeal("52773", "Tracked", "Chicken", "Japanese", "Cook it.",
                "javascript:alert(1)");
        Restaurant restaurant = Restaurant.imported("MEALDB", "Japanese", "Сакура", "ул. Ленина, 1", null);
        when(restaurantRepository.findByExternalSourceAndExternalId("MEALDB", "Japanese"))
                .thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantIdAndExternalSourceAndExternalId(any(), eq("MEALDB"), anyString()))
                .thenReturn(Optional.empty());

        writer.store(JOB, tracked, null);

        assertThat(restaurant.getImageUrl()).isNull();
        assertThat(restaurant.getMenuItems()).allSatisfy(item -> assertThat(item.getImageUrl()).isNull());
    }
}
