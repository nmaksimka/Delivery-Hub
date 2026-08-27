package com.deliveryhub.order.review;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final String AGGREGATE_TYPE = "Review";

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final Outbox outbox;
    private final Clock clock;

    public ReviewService(OrderRepository orderRepository, ReviewRepository reviewRepository, Outbox outbox, Clock clock) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public ReviewDto submit(Long orderId, Long userId, SubmitReviewRequest request) {
        Order order = findOrder(orderId);
        if (!order.belongsTo(userId)) {
            throw new ForbiddenException("Order " + orderId + " does not belong to the current user");
        }
        if (!order.isDelivered()) {
            throw new ReviewRejectedException(ErrorCode.ORDER_NOT_DELIVERED,
                    "Order " + orderId + " is " + order.getStatus() + ", only delivered orders can be reviewed");
        }
        if (reviewRepository.findByOrderId(orderId).isPresent()) {
            throw alreadyReviewed(orderId);
        }
        if (request.courierRating() != null && order.getCourierId() == null) {
            throw new ReviewRejectedException(ErrorCode.COURIER_NOT_ASSIGNED,
                    "Order " + orderId + " has no courier to rate");
        }

        Review review = Review.about(order, request.restaurantRating(), request.courierRating(),
                request.comment(), clock.instant());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw alreadyReviewed(orderId);
        }
        announce(review);
        log.info("Review {} of order {} is saved", review.getId(), orderId);
        return ReviewDto.of(review);
    }

    public ReviewDto reviewOfOrder(Long orderId, Long userId, boolean admin) {
        Order order = findOrder(orderId);
        if (!admin && !order.belongsTo(userId)) {
            throw new ForbiddenException("Order " + orderId + " does not belong to the current user");
        }
        return reviewRepository.findByOrderId(orderId)
                .map(ReviewDto::of)
                .orElseThrow(() -> new ReviewRejectedException(ErrorCode.REVIEW_NOT_FOUND,
                        "Order " + orderId + " has no review"));
    }

    public Page<ReviewDto> reviewsOfUser(Long userId, Pageable pageable) {
        return reviewRepository.findByUserId(userId, pageable).map(ReviewDto::of);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + orderId + " was not found"));
    }

    private void announce(Review review) {
        ReviewSubmittedEvent event = ReviewSubmittedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setReviewId(review.getId())
                .setOrderId(review.getOrderId())
                .setUserId(review.getUserId())
                .setRestaurantId(review.getRestaurantId())
                .setRestaurantRating(review.getRestaurantRating())
                .setCourierId(review.getCourierId())
                .setCourierRating(review.getCourierRating())
                .setSubmittedAt(review.getCreatedAt())
                .build();
        outbox.append(AGGREGATE_TYPE, review.getId(), Topics.REVIEW_SUBMITTED, event.getEventId(), event);
    }

    private static ReviewRejectedException alreadyReviewed(Long orderId) {
        return new ReviewRejectedException(ErrorCode.REVIEW_ALREADY_EXISTS, "Order " + orderId + " is already reviewed");
    }
}
