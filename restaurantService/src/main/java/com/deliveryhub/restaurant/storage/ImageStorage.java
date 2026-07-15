package com.deliveryhub.restaurant.storage;

public interface ImageStorage {

    void put(String key, byte[] content, String contentType);

    boolean exists(String key);

    void delete(String key);

    String readUrl(String key);
}
