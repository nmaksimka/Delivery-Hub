package com.deliveryhub.notification.infrastructure.users;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public class UserDirectory {

    private final RestClient restClient;

    public UserDirectory(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<UserContact> contactOf(long userId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/internal/users/{userId}/contact", userId)
                    .retrieve()
                    .body(UserContact.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public record UserContact(Long id, String email, String name) {
    }
}
