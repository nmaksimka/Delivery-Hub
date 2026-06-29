package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import feign.FeignException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class RestaurantCatalog {

    public static final String RETRY_NAME = "restaurantService";

    private final RestaurantServiceClient client;
    private final Retry readRetry;

    public RestaurantCatalog(RestaurantServiceClient client, RetryRegistry retryRegistry) {
        this.client = client;
        this.readRetry = retryRegistry.retry(RETRY_NAME);
    }

    public RestaurantDto restaurant(Long restaurantId) {
        try {
            return readRetry.executeSupplier(() -> client.getRestaurantById(restaurantId));
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Restaurant with ID: " + restaurantId + " was not found");
        }
    }

    public MenuItemDto menuItem(Long restaurantId, Long menuItemId) {
        try {
            return readRetry.executeSupplier(() -> client.getMenuItemById(restaurantId, menuItemId));
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException(
                    "Menu item with ID: " + menuItemId + " was not found in restaurant " + restaurantId);
        }
    }
}
