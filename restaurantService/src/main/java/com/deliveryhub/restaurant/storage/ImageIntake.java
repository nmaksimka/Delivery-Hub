package com.deliveryhub.restaurant.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public void discardAfterCommit(String key) {
        if (key == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorage.delete(key);
            }
        });
    }
}
