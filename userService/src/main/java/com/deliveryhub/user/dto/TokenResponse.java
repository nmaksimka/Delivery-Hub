package com.deliveryhub.user.dto;

import com.deliveryhub.user.entity.User;

import java.time.Duration;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String type,
        long expiresIn,
        Long id,
        String email,
        String name,
        String role) {

    private static final String BEARER = "Bearer";

    public static TokenResponse bearer(String accessToken, String refreshToken, Duration lifetime, User user) {
        return new TokenResponse(accessToken, refreshToken, BEARER, lifetime.toSeconds(),
                user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }
}
