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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.common.replenish-rate=1",
                "app.rate-limit.common.burst-capacity=3",
                "app.rate-limit.login.replenish-rate=1",
                "app.rate-limit.login.burst-capacity=4",
                "app.rate-limit.login.requested-tokens=2"})
@Import(RedisContainerConfig.class)
class RateLimitIT {

    private static final RSAKey USER_SERVICE_KEY = generateKey();
    private static final WireMockServer SERVICES = new WireMockServer(options().dynamicPort());

    static {
        SERVICES.start();
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    private WebTestClient client;

    @DynamicPropertySource
    static void everyServiceIsWireMock(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"userService", "orderService", "restaurantService", "deliveryService"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri", SERVICES::baseUrl);
        }
    }

    @AfterAll
    static void stopServices() {
        SERVICES.stop();
    }

    @BeforeEach
    void setUp() {
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
        SERVICES.resetAll();
        SERVICES.stubFor(get("/.well-known/jwks.json").willReturn(okJson(new JWKSet(USER_SERVICE_KEY).toString(true))));
        SERVICES.stubFor(any(urlPathMatching("/api/.*")).willReturn(okJson("{}")));
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void userWhoExhaustedLimitGetsProblemWhileAnotherUserIsServed() {
        List<Integer> statuses = new ArrayList<>();
        for (int request = 0; request < 6; request++) {
            statuses.add(statusOf("/api/orders", token(101L)));
        }

        assertThat(statuses.subList(0, 3)).containsOnly(200);
        assertThat(statuses).contains(429);
        assertThat(statusOf("/api/orders", token(102L))).isEqualTo(200);
    }

    @Test
    void rejectionIsProblemDetailWithRetryAfter() {
        WebTestClient.ResponseSpec rejected = null;
        for (int request = 0; request < 6; request++) {
            rejected = client.get().uri("/api/deliveries/1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(103L))
                    .exchange();
        }

        rejected.expectStatus().isEqualTo(429)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "1")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectBody()
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.title").isEqualTo("Too Many Requests")
                .jsonPath("$.instance").isEqualTo("/api/deliveries/1");
    }

    @Test
    void anonymousRequestsAreLimitedByAddress() {
        List<Integer> statuses = new ArrayList<>();
        for (int request = 0; request < 6; request++) {
            statuses.add(statusOf("/api/restaurants", null));
        }

        assertThat(statuses.getFirst()).isEqualTo(200);
        assertThat(statuses).contains(429);
    }

    @Test
    void loginIsLimitedStricterThanTheRestOfApi() {
        List<Integer> statuses = new ArrayList<>();
        for (int attempt = 0; attempt < 4; attempt++) {
            statuses.add(client.post().uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\": \"kate@example.com\", \"password\": \"guess-" + attempt + "\"}")
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value());
        }

        assertThat(statuses.subList(0, 2)).containsOnly(200);
        assertThat(statuses.subList(2, 4)).containsOnly(429);
        assertThat(SERVICES.findAll(postRequestedFor(urlEqualTo("/api/auth/login")))).hasSize(2);
    }

    private int statusOf(String uri, String token) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange().returnResult(String.class).getStatus().value();
    }

    private static String token(long userId) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(USER_SERVICE_KEY.getKeyID()).build(),
                    new JWTClaimsSet.Builder()
                            .issuer("deliveryhub")
                            .subject("user" + userId + "@example.com")
                            .claim("uid", userId)
                            .claim("role", "ADMIN")
                            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                            .build());
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
