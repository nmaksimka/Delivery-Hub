package com.deliveryhub.user.dto;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String name,
        String phone,
        String role,
        Instant createdAt) {
}
