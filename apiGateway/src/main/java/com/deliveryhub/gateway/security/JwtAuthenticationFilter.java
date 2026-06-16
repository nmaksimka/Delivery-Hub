package com.deliveryhub.gateway.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Проверяет JWT и пробрасывает личность вниз заголовками X-User-*.
 * Входящие X-User-* всегда затираются: иначе клиент мог бы подделать их напрямую.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_ROLE = "USER";

    private final JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Optional<Claims> claims = extractToken(exchange)
                .flatMap(jwtUtils::parseClaims);

        if (claims.isEmpty()) {
            return chain.filter(withoutIdentityHeaders(exchange));
        }

        Claims payload = claims.get();
        String role = Optional.ofNullable(jwtUtils.getRole(payload)).orElse(DEFAULT_ROLE);
        String userId = jwtUtils.getUserId(payload);
        String email = payload.getSubject();

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder
                        .headers(headers -> {
                            headers.remove(HEADER_USER_ID);
                            headers.remove(HEADER_USER_EMAIL);
                            headers.remove(HEADER_USER_ROLE);
                            if (userId != null) {
                                headers.add(HEADER_USER_ID, userId);
                            }
                            headers.add(HEADER_USER_EMAIL, email);
                            headers.add(HEADER_USER_ROLE, role);
                        }))
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        return chain.filter(mutated)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Optional<String> extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private ServerWebExchange withoutIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_EMAIL);
                    headers.remove(HEADER_USER_ROLE);
                })
                .build();
        return exchange.mutate().request(request).build();
    }
}
