package com.deliveryhub.restaurant.storage;

public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(long limitBytes) {
        super("Image is larger than " + limitBytes + " bytes");
    }
}
