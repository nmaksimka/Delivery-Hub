package com.deliveryhub.restaurant.foodfacts;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

public class FoodFactsHttp {

    public static final String CACHE = "foodfacts";

    private final RestClient restClient;

    public FoodFactsHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = CACHE, unless = "#result == null")
    public String get(String pathAndQuery) {
        return restClient.get().uri(pathAndQuery).retrieve().body(String.class);
    }
}
