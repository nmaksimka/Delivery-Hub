package com.deliveryhub.user.security;

import com.deliveryhub.user.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    /** Кладём в токен, чтобы гейтвей мог передать личность сервисам, не ходя в БД. */
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final Duration expiration;

    public JwtUtils(@Value("${app.jwt.secret}") String jwtSecret,
                    @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = Duration.ofMillis(expirationMs);
    }

    @PostConstruct
    void logConfiguration() {
        log.info("JWT configured, token lifetime: {}", expiration);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId(),
                        CLAIM_ROLE, user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(secretKey)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException err) {
            log.debug("Invalid JWT: {}", err.getMessage());
            return false;
        }
    }
}
