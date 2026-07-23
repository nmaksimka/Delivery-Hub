package com.deliveryhub.restaurant.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
public class ImageIntake {

    private static final Logger log = LoggerFactory.getLogger(ImageIntake.class);
    private static final List<String> UPLOAD_PREFIXES = List.of("restaurants/", "menu/");

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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deleteQuietly(key);
                    }
                }
            });
        }
    }

    public void discardAfterCommit(String key) {
        if (key == null || !isUpload(key)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private static boolean isUpload(String key) {
        return UPLOAD_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private void deleteQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Image {} was not deleted from storage and is left orphaned: {}", key, e.toString());
        }
    }
}
