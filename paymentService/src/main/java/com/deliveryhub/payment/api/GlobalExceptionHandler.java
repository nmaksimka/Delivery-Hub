package com.deliveryhub.payment.api;

import com.deliveryhub.payment.domain.ErrorCode;
import com.deliveryhub.payment.domain.PaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(PaymentException.class)
    public ProblemDetail handlePayment(PaymentException ex) {
        return problem(statusOf(ex.getCode()), ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        log.info("Concurrent update rejected: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "The record was changed by another request, try again",
                ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", ErrorCode.INTERNAL_ERROR);
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

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        problem.setProperty(CODE_PROPERTY, ErrorCode.VALIDATION_FAILED);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE_PROPERTY))) {
            problem.setProperty(CODE_PROPERTY, statusCode.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR
                    : ErrorCode.INVALID_REQUEST);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case PAYMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PAYMENT_INSUFFICIENT_FUNDS, PAYMENT_DECLINED, WALLET_LIMIT_EXCEEDED, IDEMPOTENCY_KEY_REUSED ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
            case PAYMENT_INVALID_STATE, PAYMENT_ORDER_MISMATCH, CONCURRENT_UPDATE -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case INVALID_AMOUNT, VALIDATION_FAILED, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }
}
