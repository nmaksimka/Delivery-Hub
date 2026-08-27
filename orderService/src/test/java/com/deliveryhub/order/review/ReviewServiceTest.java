package com.deliveryhub.order.review;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long ORDER_ID = 15L;
    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-12T18:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private Outbox outbox;

    private ReviewService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new ReviewService(orderRepository, reviewRepository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
        order = Order.place(USER_ID, 1L, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.assignCourier(9001L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        lenient().when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    @Test
    void savesReviewAndPublishesEvent() {
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        ReviewDto dto = service.submit(ORDER_ID, USER_ID, new SubmitReviewRequest(5, 4, "Отлично"));

        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.restaurantRating()).isEqualTo(5);
        assertThat(dto.courierRating()).isEqualTo(4);
        ArgumentCaptor<ReviewSubmittedEvent> event = ArgumentCaptor.forClass(ReviewSubmittedEvent.class);
        verify(outbox).append(eq("Review"), eq(3L), eq(Topics.REVIEW_SUBMITTED), any(), event.capture());
        assertThat(event.getValue().getRestaurantId()).isEqualTo(1L);
        assertThat(event.getValue().getRestaurantRating()).isEqualTo(5);
        assertThat(event.getValue().getCourierId()).isEqualTo(9001L);
        assertThat(event.getValue().getCourierRating()).isEqualTo(4);
    }

    @Test
    void rejectsReviewOfSomeoneElsesOrder() {
        assertThatThrownBy(() -> service.submit(ORDER_ID, 7L, new SubmitReviewRequest(5, null, "Текст")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reviewRepository, outbox);
    }

    @Test
    void rejectsReviewOfUndeliveredOrder() {
        ReflectionTestUtils.setField(order, "status", OrderStatus.IN_DELIVERY);

        assertThatThrownBy(() -> service.submit(ORDER_ID, USER_ID, new SubmitReviewRequest(5, null, "Текст")))
                .isInstanceOfSatisfying(ReviewRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ORDER_NOT_DELIVERED));
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsSecondReviewOfSameOrder() {
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(Review.about(order, 5, null, "Текст", NOW)));

        assertThatThrownBy(() -> service.submit(ORDER_ID, USER_ID, new SubmitReviewRequest(3, null, "Ещё")))
                .isInstanceOfSatisfying(ReviewRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
        verifyNoInteractions(outbox);
    }

    @Test
    void concurrentDuplicateIsReportedAsAlreadyReviewed() {
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenThrow(new DataIntegrityViolationException("uq_reviews_order"));

        assertThatThrownBy(() -> service.submit(ORDER_ID, USER_ID, new SubmitReviewRequest(5, null, "Текст")))
                .isInstanceOfSatisfying(ReviewRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsCourierRatingWhenOrderHasNoCourier() {
        ReflectionTestUtils.setField(order, "courierId", null);

        assertThatThrownBy(() -> service.submit(ORDER_ID, USER_ID, new SubmitReviewRequest(5, 5, "Текст")))
                .isInstanceOfSatisfying(ReviewRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.COURIER_NOT_ASSIGNED));
    }

    @Test
    void unknownOrderIsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(99L, USER_ID, new SubmitReviewRequest(5, null, "Текст")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void missingReviewOfOwnOrderIsReported() {
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reviewOfOrder(ORDER_ID, USER_ID, false))
                .isInstanceOfSatisfying(ReviewRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void adminReadsReviewOfAnyOrder() {
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(Review.about(order, 4, null, "Текст", NOW)));

        assertThat(service.reviewOfOrder(ORDER_ID, 7L, true).restaurantRating()).isEqualTo(4);
    }
}
