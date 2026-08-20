package com.deliveryhub.user.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailTaken(EmailAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ErrorCode.EMAIL_ALREADY_USED);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), ErrorCode.USER_NOT_FOUND);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ProblemDetail handleAddressNotFound(AddressNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), ErrorCode.ADDRESS_NOT_FOUND);
    }

    @ExceptionHandler(AddressNotOnMapException.class)
    public ProblemDetail handleAddressNotOnMap(AddressNotOnMapException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), ErrorCode.ADDRESS_NOT_FOUND_ON_MAP);
    }

    @ExceptionHandler(AddressLimitReachedException.class)
    public ProblemDetail handleAddressLimit(AddressLimitReachedException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ErrorCode.ADDRESS_LIMIT_REACHED);
    }

    @ExceptionHandler(GeocodingQuotaExceededException.class)
    public ProblemDetail handleGeocodingQuota(GeocodingQuotaExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ErrorCode.GEOCODING_QUOTA_EXCEEDED);
    }

    @ExceptionHandler(AdminOnlyException.class)
    public ProblemDetail handleAdminOnly(AdminOnlyException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage(), ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(OwnRoleChangeException.class)
    public ProblemDetail handleOwnRoleChange(OwnRoleChangeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ErrorCode.OWN_ROLE_CHANGE);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleBadCredentials(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Invalid email or password", ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        log.debug("Refresh token rejected: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(status, "Validation failed", ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", ErrorCode.INTERNAL_ERROR);
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }
}
