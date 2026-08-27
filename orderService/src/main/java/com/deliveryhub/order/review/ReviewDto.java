package com.deliveryhub.order.review;

import java.time.Instant;

public record ReviewDto(
        Long id,
        Long orderId,
        Long restaurantId,
        Long courierId,
        int restaurantRating,
        Integer courierRating,
        String comment,
        Instant createdAt) {

    static ReviewDto of(Review review) {
        return new ReviewDto(review.getId(), review.getOrderId(), review.getRestaurantId(), review.getCourierId(),
                review.getRestaurantRating(), review.getCourierRating(), review.getComment(), review.getCreatedAt());
    }
}
