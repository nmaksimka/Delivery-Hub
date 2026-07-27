package com.deliveryhub.restaurant.storage;

public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(long limitBytes) {
        super("Image is larger than " + limitBytes + " bytes");
    }

    public ImageTooLargeException(int width, int height, int maxSide) {
        super("Image is " + width + "x" + height + " pixels, each side must be at most " + maxSide);
    }
}
