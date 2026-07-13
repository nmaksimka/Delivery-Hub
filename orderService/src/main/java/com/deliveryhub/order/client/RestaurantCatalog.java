package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import feign.FeignException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RestaurantCatalog {

    public static final String RETRY_NAME = "restaurantService";

    private final RestaurantServiceClient client;
    private final Retry readRetry;

    public RestaurantCatalog(RestaurantServiceClient client, RetryRegistry retryRegistry) {
        this.client = client;
        this.readRetry = retryRegistry.retry(RETRY_NAME);
    }

    public MenuQuote quote(Long restaurantId, Collection<Long> menuItemIds) {
        RestaurantDto restaurant = restaurant(restaurantId);
        Map<Long, MenuItemDto> items = readRetry.executeSupplier(() -> client.getMenuItems(restaurantId, menuItemIds))
                .stream()
                .collect(Collectors.toMap(MenuItemDto::id, Function.identity()));
        return new MenuQuote(restaurant, items);
    }

    public RestaurantDto restaurant(Long restaurantId) {
        try {
            return readRetry.executeSupplier(() -> client.getRestaurantById(restaurantId));
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Restaurant with ID: " + restaurantId + " was not found");
        }
    }
}
