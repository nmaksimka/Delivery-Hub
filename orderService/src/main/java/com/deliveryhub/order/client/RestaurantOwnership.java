package com.deliveryhub.order.client;

import com.deliveryhub.order.exception.DependencyUnavailableException;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class RestaurantOwnership {

    private static final String OWNER_ROLE = "RESTAURANT_OWNER";

    private final RestClient restClient;

    public RestaurantOwnership(RestClient restClient) {
        this.restClient = restClient;
    }

    public void requireOwner(Long ownerId, Long restaurantId) {
        try {
            restClient.get()
                    .uri("/api/cabinet/restaurants/{restaurantId}", restaurantId)
                    .header("X-User-Id", String.valueOf(ownerId))
                    .header("X-User-Role", OWNER_ROLE)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ForbiddenException("Restaurant " + restaurantId + " does not belong to the user");
            }
            throw new DependencyUnavailableException(ErrorCode.RESTAURANT_UNAVAILABLE,
                    "Restaurant service rejected the ownership check with " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(ErrorCode.RESTAURANT_UNAVAILABLE,
                    "Restaurant service is unavailable", e);
        }
    }
}
