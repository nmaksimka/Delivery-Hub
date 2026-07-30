package com.deliveryhub.restaurant.storage;

public class UnsupportedImageException extends RuntimeException {

    public UnsupportedImageException() {
        super("Only JPEG, PNG and WebP images are supported");
    }
}
