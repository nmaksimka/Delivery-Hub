package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.foodfacts.FoodFactsClient;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProduct;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProperties;
import com.deliveryhub.restaurant.service.CatalogCache;
import com.deliveryhub.restaurant.storage.ImageMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class FoodFactsImport implements CatalogImport {

    private static final Logger log = LoggerFactory.getLogger(FoodFactsImport.class);

    private final FoodFactsClient foodFactsClient;
    private final ProductCatalogWriter writer;
    private final ImportProgress progress;
    private final FoodFactsProperties properties;
    private final ImageMirror imageMirror;
    private final CatalogCache catalogCache;

    public FoodFactsImport(FoodFactsClient foodFactsClient, ProductCatalogWriter writer, ImportProgress progress,
                           FoodFactsProperties properties, ImageMirror imageMirror,
                           CatalogCache catalogCache) {
        this.foodFactsClient = foodFactsClient;
        this.writer = writer;
        this.progress = progress;
        this.properties = properties;
        this.imageMirror = imageMirror;
        this.catalogCache = catalogCache;
    }

    @Override
    public ImportSource source() {
        return ImportSource.OPENFOODFACTS;
    }

    @Override
    public void run(UUID jobId) {
        try {
            progress.started(jobId);
            Map<String, Candidate> candidates = new LinkedHashMap<>();
            for (ProductShelves.Group group : ProductShelves.groups()) {
                for (FoodFactsProduct product : foodFactsClient
                        .popularInCategory(group.categoryTag(), properties.productsPerCategory())) {
                    if (product.code() != null) {
                        candidates.putIfAbsent(product.code(), new Candidate(group, product));
                    }
                }
            }
            progress.planned(jobId, candidates.size());

            for (Candidate candidate : candidates.values()) {
                String name = candidate.product().displayName();
                if (name == null || name.isBlank()) {
                    progress.skipped(jobId);
                } else {
                    String imageKey = imageMirror
                            .mirror("openfoodfacts/products/" + candidate.product().code(),
                                    candidate.product().imageUrl())
                            .orElse(null);
                    writer.store(jobId, candidate.group(), candidate.product(), imageKey);
                }
            }
            progress.completed(jobId);
            log.info("Import job {} is completed, {} products", jobId, candidates.size());
        } catch (RuntimeException e) {
            log.error("Import job {} failed", jobId, e);
            progress.failed(jobId, e.getMessage());
        } finally {
            catalogCache.evictAll();
        }
    }

    private record Candidate(ProductShelves.Group group, FoodFactsProduct product) {
    }
}
