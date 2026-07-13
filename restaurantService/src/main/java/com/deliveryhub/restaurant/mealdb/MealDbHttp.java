package com.deliveryhub.restaurant.mealdb;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

public class MealDbHttp {

    public static final String CACHE = "mealdb";

    private final RestClient restClient;

    public MealDbHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = CACHE, unless = "#result == null")
    public String get(String pathAndQuery) {
        return restClient.get().uri(pathAndQuery).retrieve().body(String.class);
    }
}
