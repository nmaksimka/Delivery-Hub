package com.deliveryhub.user.dto;

public record JwtResponse(
        String token,
        String type,
        Long id,
        String email,
        String name,
        String role) {

    private static final String BEARER = "Bearer";

    public static JwtResponse bearer(String token, Long id, String email, String name, String role) {
        return new JwtResponse(token, BEARER, id, email, name, role);
    }
}
