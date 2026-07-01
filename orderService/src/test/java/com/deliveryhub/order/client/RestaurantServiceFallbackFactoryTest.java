package com.deliveryhub.order.client;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantServiceFallbackFactoryTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "/api/restaurants/1",
            Map.of(), null, new RequestTemplate());

    private final RestaurantServiceFallbackFactory factory = new RestaurantServiceFallbackFactory();

    @Test
    void keepsClientErrorsSoCallerCanTellNotFoundFromOutage() {
        FeignException.NotFound notFound = new FeignException.NotFound("not found", REQUEST, null, Map.of());

        assertThatThrownBy(() -> factory.create(notFound).getRestaurantById(1L)).isSameAs(notFound);
    }

    @Test
    void turnsServerErrorIntoUnavailable() {
        FeignException serverError =
                new FeignException.ServiceUnavailable("unavailable", REQUEST, null, Map.of());

        assertThatThrownBy(() -> factory.create(serverError).getMenuItemById(1L, 10L))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class)
                .hasCause(serverError);
    }

    @Test
    void turnsTimeoutIntoUnavailable() {
        assertThatThrownBy(() -> factory.create(new TimeoutException("too slow")).getRestaurantById(1L))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
    }

    @Test
    void reportsOpenCircuitSeparately() {
        CallNotPermittedException rejected =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("restaurant"));

        assertThatThrownBy(() -> factory.create(rejected).getRestaurantById(1L))
                .isInstanceOf(RestaurantCircuitOpenException.class);
    }
}
