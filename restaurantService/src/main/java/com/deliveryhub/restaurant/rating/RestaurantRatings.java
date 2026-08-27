package com.deliveryhub.restaurant.rating;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.restaurant.inbox.ProcessedEvents;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.service.CatalogCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantRatings {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRatings.class);

    private final RestaurantRepository restaurantRepository;
    private final ProcessedEvents processedEvents;
    private final CatalogCache catalogCache;

    public RestaurantRatings(RestaurantRepository restaurantRepository, ProcessedEvents processedEvents,
                             CatalogCache catalogCache) {
        this.restaurantRepository = restaurantRepository;
        this.processedEvents = processedEvents;
        this.catalogCache = catalogCache;
    }

    @Transactional
    public void apply(ReviewSubmittedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.REVIEW_SUBMITTED)) {
            log.info("Review event {} is already processed, skipping", event.getEventId());
            return;
        }
        int updated = restaurantRepository.addRating(event.getRestaurantId(), event.getRestaurantRating());
        if (updated == 0) {
            log.warn("Review {} is about unknown restaurant {}", event.getReviewId(), event.getRestaurantId());
            return;
        }
        catalogCache.evictRestaurants();
        log.info("Rating {} of review {} is added to restaurant {}", event.getRestaurantRating(), event.getReviewId(),
                event.getRestaurantId());
    }
}
