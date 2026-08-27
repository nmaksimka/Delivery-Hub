package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProduct;
import com.deliveryhub.restaurant.outbox.CatalogChanges;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageSources;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class ProductCatalogWriter {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final ImportProgress progress;
    private final ImageSources imageSources;
    private final CatalogChanges catalogChanges;

    public ProductCatalogWriter(RestaurantRepository restaurantRepository,
                                MenuItemRepository menuItemRepository,
                                ImportProgress progress,
                                ImageSources imageSources,
                             CatalogChanges catalogChanges) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.progress = progress;
        this.imageSources = imageSources;
        this.catalogChanges = catalogChanges;
    }

    @Transactional
    public void store(UUID jobId, ProductShelves.Group group, FoodFactsProduct product, String imageKey) {
        String source = ImportSource.OPENFOODFACTS.name();
        ProductShelves.Shelf shelf = group.shelf();

        Optional<Restaurant> known = restaurantRepository.findByExternalSourceAndExternalId(source, shelf.externalId());
        if (known.isPresent() && known.get().hasOwner()) {
            progress.change(jobId, ImportJob::recordSkipped);
            return;
        }
        Restaurant restaurant = known.orElseGet(() -> restaurantRepository.save(newRestaurant(source, shelf)));

        String name = shortened(product.displayName(), MAX_NAME_LENGTH);
        BigDecimal price = ProductPricing.priceOf(product.code(), shelf.section());

        Optional<MenuItem> existing = menuItemRepository
                .findByRestaurantIdAndExternalSourceAndExternalId(restaurant.getId(), source, product.code());
        MenuItem item = existing.orElseGet(() -> restaurant.addMenuItem(name, null, price, group.menuCategory()));
        item.changeDetails(name, describe(product), price, group.menuCategory());
        item.changeImage(imageSources.trusted(product.imageUrl()).orElse(null));
        if (item.getImageKey() == null && imageKey != null) {
            item.storeImage(imageKey);
        }
        item.moveToSection(shelf.section());
        item.changeNutrition(ProductNutrition.of(product));
        if (existing.isEmpty()) {
            item.linkToSource(source, product.code());
            menuItemRepository.save(item);
        }

        catalogChanges.restaurantChanged(restaurant.getId());
        progress.change(jobId, job -> {
            if (known.isEmpty()) {
                job.recordRestaurantCreated();
            }
            job.recordItem(existing.isEmpty());
        });
    }

    private static Restaurant newRestaurant(String source, ProductShelves.Shelf shelf) {
        Restaurant restaurant = Restaurant.imported(source, shelf.externalId(), shelf.restaurantName(),
                shelf.address(), shelf.phone());
        restaurant.describe(shelf.cuisine(), TomskRestaurants.CITY, null);
        restaurant.changeWorkingHours(TomskRestaurants.OPENS_AT, TomskRestaurants.CLOSES_AT);
        TomskRestaurants.Location location = TomskRestaurants.locationFor(shelf.externalId());
        restaurant.locate(location.latitude(), location.longitude());
        return restaurant;
    }

    private static String describe(FoodFactsProduct product) {
        String text = Stream.of(firstBrand(product.brands()), product.quantity())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .reduce((brand, quantity) -> brand + ", " + quantity)
                .orElse(null);
        return text == null ? null : shortened(text, MAX_DESCRIPTION_LENGTH);
    }

    private static String firstBrand(String brands) {
        return brands == null ? null : brands.split(",")[0];
    }

    private static String shortened(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
