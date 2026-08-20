package com.deliveryhub.user.exception;

public class GeocodingQuotaExceededException extends RuntimeException {

    public GeocodingQuotaExceededException(int limit) {
        super("Address lookups are limited to " + limit + " per hour, try again later");
    }
}
