package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.repository.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class MealBootstrap {

    private static final Logger log = LoggerFactory.getLogger(MealBootstrap.class);

    private final BootstrapProperties properties;
    private final RestaurantRepository restaurantRepository;
    private final ImportJobService importJobService;

    public MealBootstrap(BootstrapProperties properties,
                         RestaurantRepository restaurantRepository,
                         ImportJobService importJobService) {
        this.properties = properties;
        this.restaurantRepository = restaurantRepository;
        this.importJobService = importJobService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void importMealsIfCatalogIsEmpty() {
        if (!properties.importMealsOnEmpty()) {
            return;
        }
        try {
            if (restaurantRepository.count() == 0) {
                importJobService.start(ImportSource.MEALDB);
                log.info("Catalog is empty, meal import started");
            }
        } catch (RuntimeException e) {
            log.warn("Meal import on empty catalog was not started: {}", e.getMessage());
        }
    }
}
