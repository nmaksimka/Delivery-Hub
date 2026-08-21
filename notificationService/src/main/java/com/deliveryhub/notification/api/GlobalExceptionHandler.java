package com.deliveryhub.notification.api;

import com.deliveryhub.notification.application.NotificationNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail notFound(NotificationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "NOTIFICATION_NOT_FOUND");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail missingIdentity(MissingRequestHeaderException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Request has no user identity", "UNAUTHORIZED");
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Request parameters are invalid", "VALIDATION_FAILED");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
