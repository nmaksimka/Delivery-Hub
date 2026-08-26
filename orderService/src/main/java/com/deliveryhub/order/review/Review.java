package com.deliveryhub.order.review;

import com.deliveryhub.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reviews")
public class Review {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "courier_id", updatable = false)
    private Long courierId;

    @Column(name = "restaurant_rating", nullable = false, updatable = false)
    private int restaurantRating;

    @Column(name = "courier_rating", updatable = false)
    private Integer courierRating;

    @Column(nullable = false, updatable = false, length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    private Review(Order order, int restaurantRating, Integer courierRating, String comment, Instant createdAt) {
        this.orderId = order.getId();
        this.userId = order.getUserId();
        this.restaurantId = order.getRestaurantId();
        this.courierId = courierRating == null ? null : order.getCourierId();
        this.restaurantRating = restaurantRating;
        this.courierRating = courierRating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public static Review about(Order order, int restaurantRating, Integer courierRating, String comment, Instant at) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(at, "at");
        requireRating(restaurantRating);
        if (courierRating != null) {
            requireRating(courierRating);
            if (order.getCourierId() == null) {
                throw new IllegalArgumentException("Order " + order.getId() + " has no courier to rate");
            }
        }
        return new Review(order, restaurantRating, courierRating, comment.strip(), at);
    }

    private static void requireRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Long getCourierId() {
        return courierId;
    }

    public int getRestaurantRating() {
        return restaurantRating;
    }

    public Integer getCourierRating() {
        return courierRating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
