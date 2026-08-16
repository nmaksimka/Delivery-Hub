package com.deliveryhub.user.geocoding;

public class GeocoderUnavailableException extends RuntimeException {

    public GeocoderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
