package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.mealdb.MealDbCategory;
import com.deliveryhub.restaurant.mealdb.MealDbClient;
import com.deliveryhub.restaurant.mealdb.MealDbMeal;
import com.deliveryhub.restaurant.mealdb.MealDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class MealImport implements CatalogImport {

    private static final Logger log = LoggerFactory.getLogger(MealImport.class);

    private final MealDbClient mealDbClient;
    private final MealCatalogWriter writer;
    private final ImportProgress progress;
    private final MealDbProperties properties;

    public MealImport(MealDbClient mealDbClient, MealCatalogWriter writer, ImportProgress progress,
                      MealDbProperties properties) {
        this.mealDbClient = mealDbClient;
        this.writer = writer;
        this.progress = progress;
        this.properties = properties;
    }

    @Override
    public ImportSource source() {
        return ImportSource.MEALDB;
    }

    @Override
    public void run(UUID jobId) {
        try {
            progress.started(jobId);
            Set<String> mealIds = new LinkedHashSet<>();
            for (MealDbCategory category : mealDbClient.categories()) {
                mealDbClient.mealsOfCategory(category.strCategory()).stream()
                        .limit(properties.mealsPerCategory())
                        .map(MealDbMeal::idMeal)
                        .forEach(mealIds::add);
            }
            progress.planned(jobId, mealIds.size());

            for (String mealId : mealIds) {
                Optional<MealDbMeal> meal = mealDbClient.meal(mealId);
                if (meal.isPresent() && isComplete(meal.get())) {
                    writer.store(jobId, meal.get());
                } else {
                    progress.skipped(jobId);
                }
            }
            progress.completed(jobId);
            log.info("Import job {} is completed, {} meals", jobId, mealIds.size());
        } catch (RuntimeException e) {
            log.error("Import job {} failed", jobId, e);
            progress.failed(jobId, e.getMessage());
        }
    }

    private static boolean isComplete(MealDbMeal meal) {
        return meal.idMeal() != null && meal.strMeal() != null && !meal.strMeal().isBlank();
    }
}
