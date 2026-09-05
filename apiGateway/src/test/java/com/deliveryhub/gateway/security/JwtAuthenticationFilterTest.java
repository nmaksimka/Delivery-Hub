package com.deliveryhub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";

    private JwtAuthenticationFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(new JwtUtils(SECRET));
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    @DisplayName("пробрасывает id, email и роль из валидного токена")
    void propagatesIdentityHeaders() {
        MockServerWebExchange exchange = exchangeWithToken(token(7L, "ADMIN", "admin@example.com"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(JwtAuthenticationFilter.HEADER_USER_ID)).isEqualTo("7");
        assertThat(headers.getFirst(JwtAuthenticationFilter.HEADER_USER_EMAIL)).isEqualTo("admin@example.com");
        assertThat(headers.getFirst(JwtAuthenticationFilter.HEADER_USER_ROLE)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("затирает подделанные клиентом X-User-* при валидном токене")
    void overwritesSpoofedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(7L, "USER", "user@example.com"))
                        .header(JwtAuthenticationFilter.HEADER_USER_ID, "1")
                        .header(JwtAuthenticationFilter.HEADER_USER_ROLE, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(JwtAuthenticationFilter.HEADER_USER_ID)).containsExactly("7");
        assertThat(headers.get(JwtAuthenticationFilter.HEADER_USER_ROLE)).containsExactly("USER");
    }

    @Test
    @DisplayName("удаляет подделанные X-User-*, когда токена нет вовсе")
    void stripsSpoofedHeadersWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(JwtAuthenticationFilter.HEADER_USER_ID, "1")
                        .header(JwtAuthenticationFilter.HEADER_USER_ROLE, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(JwtAuthenticationFilter.HEADER_USER_ID)).isNull();
        assertThat(headers.getFirst(JwtAuthenticationFilter.HEADER_USER_ROLE)).isNull();
    }

    @Test
    @DisplayName("не доверяет токену с чужой подписью")
    void ignoresForgedToken() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "completely-different-secret-key-32-bytes-min".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("attacker@example.com")
                .claims(Map.of(JwtUtils.CLAIM_USER_ID, 1, JwtUtils.CLAIM_ROLE, "ADMIN"))
                .signWith(otherKey)
                .compact();

        filter.filter(exchangeWithToken(forged), chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.HEADER_USER_ROLE)).isNull();
    }

    @Test
    @DisplayName("роль по умолчанию USER, если claim отсутствует")
    void defaultsToUserRole() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutRole = Jwts.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        filter.filter(exchangeWithToken(tokenWithoutRole), chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.HEADER_USER_ROLE)).isEqualTo("USER");
    }

    @Test
    @DisplayName("парсер отдаёт claims только для валидного токена")
    void parseClaimsRejectsInvalidToken() {
        JwtUtils jwtUtils = new JwtUtils(SECRET);

        assertThat(jwtUtils.parseClaims("garbage")).isEmpty();

        java.util.Optional<Claims> claims = jwtUtils.parseClaims(token(3L, "USER", "u@example.com"));
        assertThat(claims).isPresent();
        assertThat(jwtUtils.getUserId(claims.orElseThrow())).isEqualTo("3");
    }

    private static MockServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String token(long userId, String role, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(JwtUtils.CLAIM_USER_ID, userId, JwtUtils.CLAIM_ROLE, role))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
