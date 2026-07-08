package com.deliveryhub.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

public class IdentityHeadersFilter implements WebFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withHeaders(exchange, headers -> {
                    removeIdentity(headers);
                    addIdentity(headers, authentication);
                }))
                .switchIfEmpty(Mono.fromSupplier(() -> withHeaders(exchange, IdentityHeadersFilter::removeIdentity)))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withHeaders(ServerWebExchange exchange, Consumer<HttpHeaders> change) {
        return exchange.mutate().request(request -> request.headers(change)).build();
    }

    private static void removeIdentity(HttpHeaders headers) {
        headers.remove(HEADER_USER_ID);
        headers.remove(HEADER_USER_EMAIL);
        headers.remove(HEADER_USER_ROLE);
    }

    private static void addIdentity(HttpHeaders headers, JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        Object userId = jwt.getClaim(RoleFromTokenConverter.CLAIM_USER_ID);
        if (userId != null) {
            headers.add(HEADER_USER_ID, String.valueOf(userId));
        }
        headers.add(HEADER_USER_EMAIL, jwt.getSubject());
        authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(RoleFromTokenConverter.ROLE_PREFIX))
                .map(authority -> authority.substring(RoleFromTokenConverter.ROLE_PREFIX.length()))
                .findFirst()
                .ifPresent(role -> headers.add(HEADER_USER_ROLE, role));
    }
}
