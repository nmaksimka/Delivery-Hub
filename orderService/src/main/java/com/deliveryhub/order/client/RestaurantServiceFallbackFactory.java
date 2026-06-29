package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class RestaurantServiceFallbackFactory implements FallbackFactory<RestaurantServiceClient> {

    @Override
    public RestaurantServiceClient create(Throwable cause) {
        RuntimeException failure = translate(cause);
        return new RestaurantServiceClient() {
            @Override
            public RestaurantDto getRestaurantById(Long id) {
                throw failure;
            }

            @Override
            public MenuItemDto getMenuItemById(Long restaurantId, Long menuItemId) {
                throw failure;
            }
        };
    }

    private static RuntimeException translate(Throwable cause) {
        if (cause instanceof FeignException.FeignClientException clientError) {
            return clientError;
        }
        if (cause instanceof CallNotPermittedException) {
            return new RestaurantCircuitOpenException(cause);
        }
        return new RestaurantServiceUnavailableException("Restaurant service did not answer", cause);
    }
}
