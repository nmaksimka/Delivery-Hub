package com.deliveryhub.restaurant.foodfacts;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

public class FoodFactsClient {

    private static final String FIELDS =
            "code,product_name,product_name_ru,brands,quantity,image_front_url,nutriscore_grade,nutriments";

    private final FoodFactsHttp http;
    private final JsonMapper jsonMapper;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public FoodFactsClient(FoodFactsHttp http, JsonMapper jsonMapper, RateLimiter rateLimiter,
                           CircuitBreaker circuitBreaker, Retry retry) {
        this.http = http;
        this.jsonMapper = jsonMapper;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public List<FoodFactsProduct> popularInCategory(String categoryTag, int limit) {
        String path = "/api/v2/search?categories_tags_en=" + encoded(categoryTag)
                + "&fields=" + FIELDS
                + "&sort_by=unique_scans_n&page=1&page_size=" + limit;
        SearchResponse response = fetch(path);
        return response.products() == null ? List.of() : response.products();
    }

    private SearchResponse fetch(String pathAndQuery) {
        Supplier<String> limited = RateLimiter.decorateSupplier(rateLimiter, () -> http.get(pathAndQuery));
        Supplier<String> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, limited);
        try {
            String body = Retry.decorateSupplier(retry, guarded).get();
            return jsonMapper.readValue(body, SearchResponse.class);
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            throw new FoodFactsUnavailableException("Open Food Facts did not answer " + pathAndQuery, e);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new FoodFactsUnavailableException(
                    "Open Food Facts answered " + pathAndQuery + " with unreadable body", e);
        }
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record SearchResponse(List<FoodFactsProduct> products) {
    }
}
