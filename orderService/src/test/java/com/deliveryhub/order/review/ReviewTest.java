package com.deliveryhub.order.review;

import com.deliveryhub.order.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-12T18:00:00Z");

    @Test
    void reviewCopiesOrderParticipantsAndStripsComment() {
        Order order = orderWithCourier(9001L);

        Review review = Review.about(order, 5, 4, "  Вкусно и быстро  ", NOW);

        assertThat(review.getOrderId()).isEqualTo(15L);
        assertThat(review.getUserId()).isEqualTo(42L);
        assertThat(review.getRestaurantId()).isEqualTo(1L);
        assertThat(review.getCourierId()).isEqualTo(9001L);
        assertThat(review.getComment()).isEqualTo("Вкусно и быстро");
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void courierIsNotRecordedWhenCourierWasNotRated() {
        Review review = Review.about(orderWithCourier(9001L), 5, null, "Хорошо", NOW);

        assertThat(review.getCourierId()).isNull();
        assertThat(review.getCourierRating()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void rejectsRestaurantRatingOutOfRange(int rating) {
        assertThatThrownBy(() -> Review.about(orderWithCourier(9001L), rating, null, "Текст", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsCourierRatingOutOfRange(int rating) {
        assertThatThrownBy(() -> Review.about(orderWithCourier(9001L), 5, rating, "Текст", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCourierRatingWhenOrderHasNoCourier() {
        assertThatThrownBy(() -> Review.about(orderWithCourier(null), 5, 5, "Текст", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Order orderWithCourier(Long courierId) {
        Order order = Order.place(42L, 1L, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(order, "id", 15L);
        if (courierId != null) {
            order.assignCourier(courierId);
        }
        return order;
    }
}
