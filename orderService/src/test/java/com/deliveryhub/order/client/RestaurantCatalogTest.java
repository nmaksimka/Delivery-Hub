package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantCatalogTest {

    private static final RestaurantDto RESTAURANT = new RestaurantDto(1L, "Pizzeria", true);

    @Mock
    private RestaurantServiceClient client;

    private RestaurantCatalog catalog;

    @BeforeEach
    void setUp() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(RestaurantServiceUnavailableException.class)
                .ignoreExceptions(RestaurantCircuitOpenException.class)
                .build();
        catalog = new RestaurantCatalog(client, RetryRegistry.of(config));
    }

    @Test
    void repeatsReadWhenServiceIsTemporarilyUnavailable() {
        when(client.getRestaurantById(1L))
                .thenThrow(unavailable())
                .thenThrow(unavailable())
                .thenReturn(RESTAURANT);

        assertThat(catalog.restaurant(1L)).isEqualTo(RESTAURANT);
        verify(client, times(3)).getRestaurantById(1L);
    }

    @Test
    void givesUpAfterLastAttempt() {
        when(client.getMenuItemById(1L, 10L)).thenThrow(unavailable());

        assertThatThrownBy(() -> catalog.menuItem(1L, 10L))
                .isInstanceOf(RestaurantServiceUnavailableException.class);
        verify(client, times(3)).getMenuItemById(1L, 10L);
    }

    @Test
    void doesNotRepeatWhenCircuitIsOpen() {
        when(client.getRestaurantById(1L)).thenThrow(new RestaurantCircuitOpenException(null));

        assertThatThrownBy(() -> catalog.restaurant(1L)).isInstanceOf(RestaurantCircuitOpenException.class);
        verify(client, times(1)).getRestaurantById(1L);
    }

    @Test
    void missingRestaurantIsNotRetriedAndBecomesEntityNotFound() {
        when(client.getRestaurantById(1L)).thenThrow(notFound());

        assertThatThrownBy(() -> catalog.restaurant(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Restaurant with ID: 1");
        verify(client, times(1)).getRestaurantById(1L);
    }

    @Test
    void returnsMenuItemOfRestaurant() {
        MenuItemDto pizza = new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true);
        when(client.getMenuItemById(1L, 10L)).thenReturn(pizza);

        assertThat(catalog.menuItem(1L, 10L)).isEqualTo(pizza);
    }

    private static RestaurantServiceUnavailableException unavailable() {
        return new RestaurantServiceUnavailableException("Restaurant service did not answer", null);
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/restaurants/1",
                Map.of(), null, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, Map.of());
    }
}
