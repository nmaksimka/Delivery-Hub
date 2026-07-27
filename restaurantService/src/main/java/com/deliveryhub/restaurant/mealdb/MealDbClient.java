package com.deliveryhub.restaurant.mealdb;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
        CategoriesResponse response = fetch(CategoriesResponse.class, "/categories.php");
        return response.categories() == null ? List.of() : response.categories();
    }

    public List<MealDbMeal> mealsOfCategory(String category) {
        return meals("/filter.php?c={category}", category);
    }

    public Optional<MealDbMeal> meal(String mealId) {
        return meals("/lookup.php?i={id}", mealId).stream().findFirst();
    }

    public List<MealDbMeal> mealsStartingWith(char letter) {
        return meals("/search.php?f={letter}", String.valueOf(letter));
    }

    private List<MealDbMeal> meals(String uriTemplate, Object... uriVariables) {
        MealsResponse response = fetch(MealsResponse.class, uriTemplate, uriVariables);
        return response.meals() == null ? List.of() : response.meals();
    }

    private <T> T fetch(Class<T> type, String uriTemplate, Object... uriVariables) {
        try {
            String body = http.get(uriTemplate, uriVariables);
            return jsonMapper.readValue(body, type);
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            throw new MealDbUnavailableException("TheMealDB did not answer " + uriTemplate, e);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new MealDbUnavailableException("TheMealDB answered " + uriTemplate + " with unreadable body", e);
        }
    }

    private record MealsResponse(List<MealDbMeal> meals) {
    }

    private record CategoriesResponse(List<MealDbCategory> categories) {
    }
}
