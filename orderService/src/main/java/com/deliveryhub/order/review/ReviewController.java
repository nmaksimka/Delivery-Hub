package com.deliveryhub.order.review;

import com.deliveryhub.order.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto submit(@PathVariable Long orderId,
                            @RequestHeader("X-User-Id") Long userId,
                            @Valid @RequestBody SubmitReviewRequest request) {
        return reviewService.submit(orderId, userId, request);
    }

    @GetMapping("/{orderId}/review")
    public ReviewDto reviewOfOrder(@PathVariable Long orderId,
                                   @RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return reviewService.reviewOfOrder(orderId, userId, CurrentUser.isAdmin(role));
    }

    @GetMapping("/reviews/mine")
    public Page<ReviewDto> myReviews(@RequestHeader("X-User-Id") Long userId,
                                     @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable) {
        return reviewService.reviewsOfUser(userId, pageable);
    }
}
