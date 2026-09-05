package com.deliveryhub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtils {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;

    public JwtUtils(@Value("${app.jwt.secret}") String jwtSecret) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Единая точка разбора: либо валидные claims, либо пусто.
     * Отдельные validate/parse приводили к двойному разбору одного токена.
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException err) {
            log.debug("Rejected JWT: {}", err.getMessage());
            return Optional.empty();
        }
    }

    public String getUserId(Claims claims) {
        Object userId = claims.get(CLAIM_USER_ID);
        return userId == null ? null : String.valueOf(userId);
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
