package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.exception.DependencyUnavailableException;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ItemNotFoundException;
import com.deliveryhub.order.exception.UnprocessableOrderException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class UserAddresses {

    private final RestClient restClient;

    public UserAddresses(RestClient restClient) {
        this.restClient = restClient;
    }

    public UserAddressDto deliverableAddress(Long userId, Long addressId) {
        UserAddressDto address = address(userId, addressId);
        if (!address.onMap()) {
            throw new UnprocessableOrderException(ErrorCode.ADDRESS_NOT_GEOCODED,
                    "Address " + addressId + " has no coordinates yet, delivery cannot be calculated");
        }
        return address;
    }

    public UserAddressDto address(Long userId, Long addressId) {
        try {
            UserAddressDto address = restClient.get()
                    .uri("/internal/users/{userId}/addresses/{addressId}", userId, addressId)
                    .retrieve()
                    .body(UserAddressDto.class);
            if (address == null) {
                throw new DependencyUnavailableException(ErrorCode.USER_SERVICE_UNAVAILABLE,
                        "User service answered without address", null);
            }
            return address;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ItemNotFoundException(ErrorCode.ADDRESS_NOT_FOUND,
                        "Address with ID: " + addressId + " was not found");
            }
            throw new DependencyUnavailableException(ErrorCode.USER_SERVICE_UNAVAILABLE,
                    "User service rejected the address request with " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(ErrorCode.USER_SERVICE_UNAVAILABLE,
                    "User service is unavailable", e);
        }
    }
}
