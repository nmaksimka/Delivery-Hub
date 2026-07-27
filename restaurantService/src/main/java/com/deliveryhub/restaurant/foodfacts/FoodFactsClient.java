package com.deliveryhub.restaurant.foodfacts;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

public class FoodFactsClient {

    private static final String SEARCH = "/api/v2/search?categories_tags_en={category}&fields={fields}"
            + "&sort_by=unique_scans_n&page=1&page_size={limit}";
    private static final String FIELDS =
            "code,product_name,product_name_ru,brands,quantity,image_front_url,nutriscore_grade,nutriments";

    private final FoodFactsHttp http;
    private final JsonMapper jsonMapper;

    public FoodFactsClient(FoodFactsHttp http, JsonMapper jsonMapper) {
        this.http = http;
        this.jsonMapper = jsonMapper;
    }

    public List<FoodFactsProduct> popularInCategory(String categoryTag, int limit) {
        SearchResponse response = fetch(SEARCH, categoryTag, FIELDS, limit);
        return response.products() == null ? List.of() : response.products();
    }

    private SearchResponse fetch(String uriTemplate, Object... uriVariables) {
        try {
            String body = http.get(uriTemplate, uriVariables);
            return jsonMapper.readValue(body, SearchResponse.class);
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            throw new FoodFactsUnavailableException("Open Food Facts did not answer " + uriTemplate, e);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new FoodFactsUnavailableException(
                    "Open Food Facts answered " + uriTemplate + " with unreadable body", e);
        }
    }

    private record SearchResponse(List<FoodFactsProduct> products) {
    }
}
