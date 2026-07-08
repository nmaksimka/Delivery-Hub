package com.deliveryhub.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.jwt")
public record GatewayJwtProperties(
        @DefaultValue("http://userService/.well-known/jwks.json") String jwkSetUri,
        @DefaultValue("deliveryhub") String issuer) {
}
