package com.deliveryhub.gateway.ratelimit;

import com.deliveryhub.gateway.security.RoleFromTokenConverter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class ClientKeyResolvers {

    private static final String USER_KEY_PREFIX = "user:";
    private static final String IP_KEY_PREFIX = "ip:";
    private static final String LOGIN_KEY_PREFIX = "login:";
    private static final String UNKNOWN_ADDRESS = "unknown";

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .mapNotNull(authentication -> authentication.getToken().getClaim(RoleFromTokenConverter.CLAIM_USER_ID))
                .map(userId -> USER_KEY_PREFIX + userId)
                .switchIfEmpty(Mono.fromSupplier(() -> ipKey(exchange)));
    }

    @Bean
    public KeyResolver loginAttemptKeyResolver() {
        return exchange -> Mono.just(LOGIN_KEY_PREFIX + ipKey(exchange));
    }

    private static String ipKey(ServerWebExchange exchange) {
        return IP_KEY_PREFIX + Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse(UNKNOWN_ADDRESS);
    }
}
