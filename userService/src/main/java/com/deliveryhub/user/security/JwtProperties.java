package com.deliveryhub.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @DefaultValue("deliveryhub") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        String privateKey) {

    public boolean hasPrivateKey() {
        return privateKey != null && !privateKey.isBlank();
    }
}
