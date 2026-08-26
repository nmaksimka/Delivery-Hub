package com.deliveryhub.delivery;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.repository.CourierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class CourierRatingIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private CourierRepository courierRepository;

    @Test
    void courierRatingIsAveragedFromReviewEvents() {
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "1", review(9002L, 5));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "2", review(9002L, 4));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "3", review(9002L, 4));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(courier(9002L).getRatingCount()).isEqualTo(3));
        assertThat(courier(9002L).getRating()).isEqualTo(4.3);
    }

    @Test
    void redeliveredEventIsCountedOnce() {
        ReviewSubmittedEvent event = review(9003L, 2);

        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "1", event);
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "1", event);
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "2", review(9003L, 4));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(courier(9003L).getRatingCount()).isEqualTo(2));
        assertThat(courier(9003L).getRating()).isEqualTo(3.0);
    }

    @Test
    void reviewWithoutCourierAndUnknownCourierAreIgnored() {
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "1", new ReviewSubmittedEvent(UUID.randomUUID(), 1L, 1L, 42L, 1L,
                5, null, null, Instant.now()));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "2", review(999_999L, 5));
        kafkaTemplate.send(Topics.REVIEW_SUBMITTED, "3", review(9004L, 5));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(courier(9004L).getRatingCount()).isEqualTo(1));
        assertThat(courier(9001L).getRating()).isNull();
    }

    private Courier courier(long id) {
        return courierRepository.findById(id).orElseThrow();
    }

    private static ReviewSubmittedEvent review(long courierId, int courierRating) {
        return new ReviewSubmittedEvent(UUID.randomUUID(), 1L, 1L, 42L, 1L, 5, courierId, courierRating, Instant.now());
    }
}
