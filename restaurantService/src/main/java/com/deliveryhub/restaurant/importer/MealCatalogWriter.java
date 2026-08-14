package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mealdb.MealDbMeal;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageSources;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class MealCatalogWriter {

    private static final int MAX_NAME_LENGTH = 255;

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final ImportProgress progress;
    private final ImageSources imageSources;

    public MealCatalogWriter(RestaurantRepository restaurantRepository,
                             MenuItemRepository menuItemRepository,
                             ImportProgress progress,
                             ImageSources imageSources) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.progress = progress;
        this.imageSources = imageSources;
    }

    @Transactional
    public void store(UUID jobId, MealDbMeal meal, String imageKey) {
        String source = ImportSource.MEALDB.name();
        String area = TomskRestaurants.areaKey(meal.strArea());

        Optional<Restaurant> known = restaurantRepository.findByExternalSourceAndExternalId(source, area);
        if (known.isPresent() && known.get().hasOwner()) {
            progress.change(jobId, ImportJob::recordSkipped);
            return;
        }
        Restaurant restaurant = known.orElseGet(() -> restaurantRepository.save(newRestaurant(source, area)));
        String imageUrl = imageSources.trusted(meal.strMealThumb()).orElse(null);
        restaurant.useImportedImage(imageUrl);
        if (restaurant.getImageKey() == null && imageKey != null) {
            restaurant.storeImage(imageKey);
        }

        String name = shortened(meal.strMeal());
        String description = MealDescription.fromInstructions(meal.strInstructions());
        BigDecimal price = MealPricing.priceOf(meal.idMeal(), meal.strCategory());
        String category = TomskRestaurants.categoryName(meal.strCategory());

        Optional<MenuItem> existing = menuItemRepository
                .findByRestaurantIdAndExternalSourceAndExternalId(restaurant.getId(), source, meal.idMeal());
        MenuItem item = existing.orElseGet(() -> restaurant.addMenuItem(name, description, price, category));
        item.changeDetails(name, description, price, category);
        item.changeImage(imageUrl);
        if (item.getImageKey() == null && imageKey != null) {
            item.storeImage(imageKey);
        }
        if (existing.isEmpty()) {
            item.linkToSource(source, meal.idMeal());
            menuItemRepository.save(item);
        }

        progress.change(jobId, job -> {
            if (known.isEmpty()) {
                job.recordRestaurantCreated();
            }
            job.recordItem(existing.isEmpty());
        });
    }

    private static Restaurant newRestaurant(String source, String area) {
        TomskRestaurants.Profile profile = TomskRestaurants.forArea(area);
        Restaurant restaurant = Restaurant.imported(source, area, profile.name(),
                TomskRestaurants.addressFor(area), TomskRestaurants.phoneFor(area));
        restaurant.describe(profile.cuisine(), TomskRestaurants.CITY, null);
        restaurant.changeWorkingHours(TomskRestaurants.OPENS_AT, TomskRestaurants.CLOSES_AT);
        TomskRestaurants.Location location = TomskRestaurants.locationFor(area);
        restaurant.locate(location.latitude(), location.longitude());
        return restaurant;
    }

    private static String shortened(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
