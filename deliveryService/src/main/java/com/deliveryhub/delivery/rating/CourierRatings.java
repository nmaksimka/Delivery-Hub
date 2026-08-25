package com.deliveryhub.delivery.rating;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.repository.CourierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourierRatings {

    private static final Logger log = LoggerFactory.getLogger(CourierRatings.class);

    private final CourierRepository courierRepository;
    private final ProcessedEvents processedEvents;

    public CourierRatings(CourierRepository courierRepository, ProcessedEvents processedEvents) {
        this.courierRepository = courierRepository;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void apply(ReviewSubmittedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.REVIEW_SUBMITTED)) {
            log.info("Review event {} is already processed, skipping", event.getEventId());
            return;
        }
        if (event.getCourierId() == null || event.getCourierRating() == null) {
            return;
        }
        int updated = courierRepository.addRating(event.getCourierId(), event.getCourierRating());
        if (updated == 0) {
            log.warn("Review {} is about unknown courier {}", event.getReviewId(), event.getCourierId());
            return;
        }
        log.info("Rating {} of review {} is added to courier {}", event.getCourierRating(), event.getReviewId(),
                event.getCourierId());
    }
}
