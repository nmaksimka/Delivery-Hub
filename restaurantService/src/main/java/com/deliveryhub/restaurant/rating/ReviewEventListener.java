package com.deliveryhub.restaurant.rating;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final RestaurantRatings ratings;

    public ReviewEventListener(RestaurantRatings ratings) {
        this.ratings = ratings;
    }

    @KafkaListener(topics = Topics.REVIEW_SUBMITTED, groupId = "restaurant-service-group")
    public void handleReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("Received review-submitted event: eventId={}, restaurantId={}", event.getEventId(), event.getRestaurantId());
        ratings.apply(event);
    }
}
