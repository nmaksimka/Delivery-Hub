package com.deliveryhub.restaurant.storage;

import org.springframework.stereotype.Component;

@Component
public class ImageIntake {

    private final ImageStorage imageStorage;
    private final StorageProperties properties;

    public ImageIntake(ImageStorage imageStorage, StorageProperties properties) {
        this.imageStorage = imageStorage;
        this.properties = properties;
    }

    public void save(String key, byte[] content) {
        long limit = properties.maxImageSize().toBytes();
        if (content.length > limit) {
            throw new ImageTooLargeException(limit);
        }
        ImageFormat format = ImageFormat.detect(content).orElseThrow(UnsupportedImageException::new);
        imageStorage.put(key, content, format.contentType());
    }
}
