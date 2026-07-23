package com.deliveryhub.restaurant.mealdb;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class MealDbClient {

    private final MealDbHttp http;
    private final JsonMapper jsonMapper;

    public MealDbClient(MealDbHttp http, JsonMapper jsonMapper) {
        this.http = http;
        this.jsonMapper = jsonMapper;
    }

    public List<MealDbCategory> categories() {
        CategoriesResponse response = fetch("/categories.php", CategoriesResponse.class);
        return response.categories() == null ? List.of() : response.categories();
    }

    public List<MealDbMeal> mealsOfCategory(String category) {
        return meals("/filter.php?c=" + encoded(category));
    }

    public Optional<MealDbMeal> meal(String mealId) {
        return meals("/lookup.php?i=" + encoded(mealId)).stream().findFirst();
    }

    public List<MealDbMeal> mealsStartingWith(char letter) {
        return meals("/search.php?f=" + encoded(String.valueOf(letter)));
    }

    private List<MealDbMeal> meals(String pathAndQuery) {
        MealsResponse response = fetch(pathAndQuery, MealsResponse.class);
        return response.meals() == null ? List.of() : response.meals();
    }

    private <T> T fetch(String pathAndQuery, Class<T> type) {
        try {
            String body = http.get(pathAndQuery);
            return jsonMapper.readValue(body, type);
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            throw new MealDbUnavailableException("TheMealDB did not answer " + pathAndQuery, e);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new MealDbUnavailableException("TheMealDB answered " + pathAndQuery + " with unreadable body", e);
        }
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record MealsResponse(List<MealDbMeal> meals) {
    }

    private record CategoriesResponse(List<MealDbCategory> categories) {
    }
}
