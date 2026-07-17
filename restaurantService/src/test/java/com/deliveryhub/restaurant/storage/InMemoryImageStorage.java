package com.deliveryhub.restaurant.storage;

import java.util.HashMap;
import java.util.Map;

public class InMemoryImageStorage implements ImageStorage {

    private final Map<String, byte[]> contents = new HashMap<>();
    private final Map<String, String> contentTypes = new HashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        contents.put(key, content);
        contentTypes.put(key, contentType);
    }

    @Override
    public boolean exists(String key) {
        return contents.containsKey(key);
    }

    @Override
    public void delete(String key) {
        contents.remove(key);
        contentTypes.remove(key);
    }

    @Override
    public String readUrl(String key) {
        return "http://storage.test/images/" + key + "?signed";
    }

    public byte[] contentOf(String key) {
        return contents.get(key);
    }

    public String contentTypeOf(String key) {
        return contentTypes.get(key);
    }

    public int size() {
        return contents.size();
    }
}
