package com.deliveryhub.order.client;

import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.dto.GeoLocation;
import com.deliveryhub.order.exception.DependencyUnavailableException;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.UnprocessableOrderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class DeliveryQuotes {

    private static final String OUT_OF_ZONE = "OUT_OF_DELIVERY_ZONE";

    private final RestClient restClient;

    public DeliveryQuotes(RestClient restClient) {
        this.restClient = restClient;
    }

    public DeliveryQuoteDto quote(Long restaurantId, GeoLocation restaurant, GeoLocation customer) {
        try {
            DeliveryQuoteDto quote = restClient.post()
                    .uri("/internal/delivery/quote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new QuoteRequest(restaurantId, restaurant, customer))
                    .retrieve()
                    .body(DeliveryQuoteDto.class);
            if (quote == null) {
                throw new DependencyUnavailableException(ErrorCode.DELIVERY_UNAVAILABLE,
                        "Delivery service answered without quote", null);
            }
            return quote;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)
                    && e.getResponseBodyAsString().contains(OUT_OF_ZONE)) {
                throw new UnprocessableOrderException(ErrorCode.OUT_OF_DELIVERY_ZONE,
                        "Address is outside the delivery zone of restaurant " + restaurantId);
            }
            throw new DependencyUnavailableException(ErrorCode.DELIVERY_UNAVAILABLE,
                    "Delivery service rejected the quote request with " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(ErrorCode.DELIVERY_UNAVAILABLE,
                    "Delivery service is unavailable", e);
        }
    }

    record QuoteRequest(Long restaurantId, GeoLocation from, GeoLocation to) {
    }
}
