package com.deliveryhub.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityHeadersFilterTest {

    private static final String INTERNAL_TOKEN = "gateway-secret";

    private final IdentityHeadersFilter filter = new IdentityHeadersFilter(INTERNAL_TOKEN);
    private final RoleFromTokenConverter converter = new RoleFromTokenConverter();

    private AtomicReference<ServerWebExchange> forwarded;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void propagatesIdEmailAndRoleFromToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        filterAs(exchange, authenticated(jwt(7L, "ADMIN", "admin@example.com")));

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(IdentityHeadersFilter.HEADER_USER_ID)).containsExactly("7");
        assertThat(headers.get(IdentityHeadersFilter.HEADER_USER_EMAIL)).containsExactly("admin@example.com");
        assertThat(headers.get(IdentityHeadersFilter.HEADER_USER_ROLE)).containsExactly("ADMIN");
        assertThat(headers.getFirst(IdentityHeadersFilter.HEADER_USER_TOKEN_EXP)).matches("\\d+");
    }

    @Test
    void overwritesHeadersSpoofedByClient() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(IdentityHeadersFilter.HEADER_USER_ID, "1")
                .header(IdentityHeadersFilter.HEADER_USER_ROLE, "ADMIN"));

        filterAs(exchange, authenticated(jwt(7L, "USER", "user@example.com")));

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(IdentityHeadersFilter.HEADER_USER_ID)).containsExactly("7");
        assertThat(headers.get(IdentityHeadersFilter.HEADER_USER_ROLE)).containsExactly("USER");
    }

    @Test
    void stripsSpoofedHeadersFromAnonymousRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/restaurants")
                .header(IdentityHeadersFilter.HEADER_USER_ID, "1")
                .header(IdentityHeadersFilter.HEADER_USER_EMAIL, "admin@example.com")
                .header(IdentityHeadersFilter.HEADER_USER_ROLE, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(IdentityHeadersFilter.HEADER_USER_ID)).isNull();
        assertThat(headers.getFirst(IdentityHeadersFilter.HEADER_USER_EMAIL)).isNull();
        assertThat(headers.getFirst(IdentityHeadersFilter.HEADER_USER_ROLE)).isNull();
    }

    @Test
    void replacesInternalTokenSentByClientWithGatewaySecret() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/restaurants")
                .header(IdentityHeadersFilter.HEADER_INTERNAL_TOKEN, "guessed"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().get(IdentityHeadersFilter.HEADER_INTERNAL_TOKEN))
                .containsExactly(INTERNAL_TOKEN);
    }

    @Test
    void addsInternalTokenToAuthenticatedRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        filterAs(exchange, authenticated(jwt(7L, "USER", "user@example.com")));

        assertThat(forwarded.get().getRequest().getHeaders().get(IdentityHeadersFilter.HEADER_INTERNAL_TOKEN))
                .containsExactly(INTERNAL_TOKEN);
    }

    @Test
    void doesNotTrustAuthenticationThatIsNotJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(IdentityHeadersFilter.HEADER_USER_ID, "1"));

        filterAs(exchange, new UsernamePasswordAuthenticationToken("someone", "password", List.of()));

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(IdentityHeadersFilter.HEADER_USER_ID)).isNull();
    }

    @Test
    void roleDefaultsToUserWhenClaimIsMissing() {
        Jwt withoutRole = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user@example.com")
                .claim(RoleFromTokenConverter.CLAIM_USER_ID, 7L)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        filterAs(exchange, authenticated(withoutRole));

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(IdentityHeadersFilter.HEADER_USER_ROLE))
                .isEqualTo("USER");
    }

    private void filterAs(ServerWebExchange exchange, org.springframework.security.core.Authentication authentication) {
        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();
    }

    private AbstractAuthenticationToken authenticated(Jwt jwt) {
        return converter.convert(jwt).block();
    }

    private static Jwt jwt(long userId, String role, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(email)
                .claim(RoleFromTokenConverter.CLAIM_USER_ID, userId)
                .claim(RoleFromTokenConverter.CLAIM_ROLE, role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
