package com.deliveryhub.order.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @NotNull @Min(Review.MIN_RATING) @Max(Review.MAX_RATING) Integer restaurantRating,
        @Min(Review.MIN_RATING) @Max(Review.MAX_RATING) Integer courierRating,
        @NotBlank @Size(max = 2000) String comment) {
}
