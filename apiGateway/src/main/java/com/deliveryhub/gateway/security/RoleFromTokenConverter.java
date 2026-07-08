package com.deliveryhub.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public class RoleFromTokenConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String ROLE_PREFIX = "ROLE_";

    private static final String DEFAULT_ROLE = "USER";

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        String role = Optional.ofNullable(jwt.getClaimAsString(CLAIM_ROLE)).orElse(DEFAULT_ROLE);
        return Mono.just(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)), jwt.getSubject()));
    }
}
