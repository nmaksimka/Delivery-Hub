package com.deliveryhub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(@NotBlank(message = "Refresh token is required") @Size(max = 512) String refreshToken) {
}
