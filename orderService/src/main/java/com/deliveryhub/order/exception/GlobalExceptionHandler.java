package com.deliveryhub.order.exception;

import com.deliveryhub.order.client.InvalidRestaurantRequestException;
import com.deliveryhub.order.client.RestaurantServiceUnavailableException;
import com.deliveryhub.order.idempotency.MissingIdempotencyKeyException;
import com.deliveryhub.order.review.ReviewRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "10";
    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), codeOf(ex, ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(DeliveryFeeChangedException.class)
    public ProblemDetail handleDeliveryFeeChanged(DeliveryFeeChangedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage(), ex.code());
        problem.setProperty("deliveryFee", ex.deliveryFee());
        return problem;
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, OrderRejectedException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), codeOf(ex, ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @ExceptionHandler(ReviewRejectedException.class)
    public ProblemDetail handleReviewRejected(ReviewRejectedException ex) {
        HttpStatus status = switch (ex.code()) {
            case REVIEW_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case COURIER_NOT_ASSIGNED -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.CONFLICT;
        };
        return problem(status, ex.getMessage(), ex.code());
    }

    @ExceptionHandler(InvalidRestaurantRequestException.class)
    public ProblemDetail handleInvalidRestaurantRequest(InvalidRestaurantRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(InvalidPromoException.class)
    public ProblemDetail handleInvalidPromo(InvalidPromoException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.code());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage(), ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        log.info("Concurrent update rejected: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.CONFLICT,
                "The record was changed by another request, reload it and try again", ErrorCode.CONCURRENT_UPDATE);
        problem.setTitle("Concurrent update");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        problem.setProperty(CODE_PROPERTY, ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(RestaurantServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleRestaurantUnavailable(RestaurantServiceUnavailableException ex) {
        log.warn("Restaurant service is unavailable: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE,
                "Restaurant service is temporarily unavailable, try again later", ErrorCode.RESTAURANT_UNAVAILABLE);
        problem.setTitle("Restaurant service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    @ExceptionHandler(UnprocessableOrderException.class)
    public ProblemDetail handleUnprocessable(UnprocessableOrderException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), ex.code());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleDependencyUnavailable(DependencyUnavailableException ex) {
        log.warn("Dependency is unavailable: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex.code());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", ErrorCode.INTERNAL_ERROR);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }

    private static ErrorCode codeOf(RuntimeException ex, ErrorCode fallback) {
        return ex instanceof Coded coded ? coded.code() : fallback;
    }
}
