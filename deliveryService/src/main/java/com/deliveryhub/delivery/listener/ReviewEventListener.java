package com.deliveryhub.delivery.listener;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.delivery.rating.CourierRatings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final CourierRatings ratings;

    public ReviewEventListener(CourierRatings ratings) {
        this.ratings = ratings;
    }

    @KafkaListener(topics = Topics.REVIEW_SUBMITTED, groupId = "delivery-service-group")
    public void handleReviewSubmitted(ReviewSubmittedEvent event) {
        log.info("Received review-submitted event: eventId={}, courierId={}", event.getEventId(), event.getCourierId());
        ratings.apply(event);
    }
}
