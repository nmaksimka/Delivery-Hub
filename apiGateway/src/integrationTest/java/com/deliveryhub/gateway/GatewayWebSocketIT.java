package com.deliveryhub.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisContainerConfig.class)
class GatewayWebSocketIT {

    private static final RSAKey USER_SERVICE_KEY = generateKey();
    private static final WireMockServer USER_SERVICE = new WireMockServer(options().dynamicPort());
    private static final DisposableServer DELIVERY_SERVICE = HttpServer.create()
            .port(0)
            .route(routes -> routes.get("/ws/tracking", (request, response) -> response.sendWebsocket(
                    (inbound, outbound) -> outbound.sendString(Mono.just(String.join("|",
                            request.uri(),
                            String.valueOf(request.requestHeaders().get("X-User-Id")),
                            String.valueOf(request.requestHeaders().get("X-User-Role")),
                            String.valueOf(request.requestHeaders().get("X-Internal-Token"))))))))
            .bindNow();

    static {
        USER_SERVICE.start();
    }

    @Value("${local.server.port}")
    private int port;

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.userService[0].uri", USER_SERVICE::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.deliveryService[0].uri",
                () -> "http://localhost:" + DELIVERY_SERVICE.port());
    }

    @AfterAll
    static void stopServices() {
        USER_SERVICE.stop();
        DELIVERY_SERVICE.disposeNow();
    }

    @BeforeEach
    void setUp() {
        USER_SERVICE.resetAll();
        USER_SERVICE.stubFor(get("/.well-known/jwks.json")
                .willReturn(okJson(new JWKSet(USER_SERVICE_KEY).toString(true))));
    }

    @Test
    void tokenInQueryOpensSocketWithIdentityHeadersAndIsNotForwarded() {
        String greeting = firstMessage("/ws/tracking?access_token=" + token(7L, "USER", 600), new HttpHeaders());

        assertThat(greeting).isEqualTo("/ws/tracking|7|USER|local-dev-internal-token");
    }

    @Test
    void tokenInAuthorizationHeaderAlsoWorksAndSpoofedIdentityIsReplaced() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(9L, "COURIER", 600));
        headers.add("X-User-Id", "1");
        headers.add("X-User-Role", "ADMIN");

        assertThat(firstMessage("/ws/tracking", headers)).isEqualTo("/ws/tracking|9|COURIER|local-dev-internal-token");
    }

    @Test
    void socketWithoutTokenIsRejectedAtHandshake() {
        assertThatThrownBy(() -> firstMessage("/ws/tracking", new HttpHeaders()))
                .hasStackTraceContaining("401");
    }

    @Test
    void socketWithExpiredOrForgedTokenIsRejectedAtHandshake() {
        assertThatThrownBy(() -> firstMessage("/ws/tracking?access_token=" + token(7L, "USER", -600), new HttpHeaders()))
                .hasStackTraceContaining("401");
        assertThatThrownBy(() -> firstMessage("/ws/tracking?access_token=not-a-jwt", new HttpHeaders()))
                .hasStackTraceContaining("401");
    }

    @Test
    void queryTokenIsIgnoredOutsideWebSockets() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build()
                .get().uri("/api/orders?access_token=" + token(7L, "USER", 600))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String firstMessage(String path, HttpHeaders headers) {
        AtomicReference<String> message = new AtomicReference<>();
        client.execute(URI.create("ws://localhost:" + port + path), headers, session -> session.receive()
                        .take(1)
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(message::set)
                        .then())
                .block(Duration.ofSeconds(10));
        return message.get();
    }

    private static String token(long userId, String role, long expiresInSeconds) {
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("deliveryhub")
                .subject(role.toLowerCase() + "@example.com")
                .claim("uid", userId)
                .claim("role", role)
                .issueTime(Date.from(expiresAt.minusSeconds(900)))
                .expirationTime(Date.from(expiresAt))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(USER_SERVICE_KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(USER_SERVICE_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("user-service-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
