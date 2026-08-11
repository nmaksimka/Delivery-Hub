package com.deliveryhub.order.client;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.contracts.pricing.QuotedItem;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ItemNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class RestaurantCatalog {

    public static final String RESILIENCE_NAME = "restaurantService";

    private final MenuPricingGrpc.MenuPricingBlockingStub stub;
    private final RestaurantGrpcProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public RestaurantCatalog(MenuPricingGrpc.MenuPricingBlockingStub stub,
                             RestaurantGrpcProperties properties,
                             CircuitBreakerRegistry circuitBreakerRegistry,
                             RetryRegistry retryRegistry) {
        this.stub = stub;
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_NAME, RESILIENCE_NAME);
        this.retry = retryRegistry.retry(RESILIENCE_NAME);
    }

    public MenuQuote quote(Long restaurantId, Collection<Long> menuItemIds) {
        QuoteRequest request = QuoteRequest.newBuilder()
                .setRestaurantId(restaurantId)
                .addAllMenuItemIds(menuItemIds)
                .build();
        Supplier<QuoteResponse> call = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> send(request)));
        try {
            return toQuote(call.get());
        } catch (CallNotPermittedException e) {
            throw new RestaurantCircuitOpenException(e);
        }
    }

    private QuoteResponse send(QuoteRequest request) {
        try {
            return stub.withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS).quote(request);
        } catch (StatusRuntimeException e) {
            throw translate(e, request.getRestaurantId());
        }
    }

    private static RuntimeException translate(StatusRuntimeException e, long restaurantId) {
        Status.Code code = e.getStatus().getCode();
        if (code == Status.Code.NOT_FOUND) {
            return new ItemNotFoundException(ErrorCode.RESTAURANT_NOT_FOUND,
                    "Restaurant with ID: " + restaurantId + " was not found");
        }
        if (code == Status.Code.INVALID_ARGUMENT) {
            return new InvalidRestaurantRequestException("Restaurant service rejected the request: "
                    + e.getStatus().getDescription(), e);
        }
        if (code == Status.Code.UNAVAILABLE) {
            return new RestaurantUnreachableException(e);
        }
        return new RestaurantServiceUnavailableException("Restaurant service answered " + code, e);
    }

    private static String workingHours(QuoteResponse response) {
        if (response.getOpensAt().isEmpty() || response.getClosesAt().isEmpty()) {
            return "";
        }
        return (response.getOpensAt() + "-" + response.getClosesAt() + " " + response.getTimeZone()).strip();
    }

    private static MenuQuote toQuote(QuoteResponse response) {
        RestaurantDto restaurant = new RestaurantDto(response.getRestaurantId(), response.getRestaurantName(),
                response.getRestaurantActive(), response.getRestaurantClosed(), workingHours(response));
        Map<Long, MenuItemDto> items = new HashMap<>();
        for (QuotedItem item : response.getItemsList()) {
            if (item.getStatus() == ItemStatus.ITEM_STATUS_AVAILABLE
                    || item.getStatus() == ItemStatus.ITEM_STATUS_UNAVAILABLE) {
                items.put(item.getMenuItemId(), new MenuItemDto(item.getMenuItemId(), item.getName(),
                        new BigDecimal(item.getPrice()), item.getStatus() == ItemStatus.ITEM_STATUS_AVAILABLE));
            }
        }
        return new MenuQuote(restaurant, items);
    }
}
