package com.deliveryhub.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisContainerConfig.class)
class GatewaySecurityIT {

    private static final String ISSUER = "deliveryhub";
    private static final String JWKS_URL = "/.well-known/jwks.json";
    private static final RSAKey USER_SERVICE_KEY = generateKey("user-service-key");
    private static final RSAKey ATTACKER_KEY = generateKey("user-service-key");
    private static final WireMockServer SERVICES = new WireMockServer(options().dynamicPort());

    static {
        SERVICES.start();
    }

    @Value("${local.server.port}")
    private int port;

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
        SERVICES.resetAll();
        SERVICES.stubFor(get(JWKS_URL).willReturn(okJson(new JWKSet(USER_SERVICE_KEY).toString(true))));
        SERVICES.stubFor(any(urlPathMatching("/api/.*")).willReturn(okJson("{}")));
        SERVICES.stubFor(get("/v3/api-docs").willReturn(okJson("{}")));
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void validTokenReachesServiceWithIdentityHeaders() {
        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(7L, "USER", "kate@example.com")))
                .exchange()
                .expectStatus().isOk();

        SERVICES.verify(getRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("X-User-Id", equalTo("7"))
                .withHeader("X-User-Email", equalTo("kate@example.com"))
                .withHeader("X-User-Role", equalTo("USER"))
                .withHeader("X-Internal-Token", equalTo("local-dev-internal-token")));
    }

    @Test
    void identityHeadersSentByClientNeverReachService() {
        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(7L, "USER", "kate@example.com")))
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/restaurants")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        SERVICES.verify(getRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("X-User-Id", equalTo("7"))
                .withHeader("X-User-Role", equalTo("USER")));
        SERVICES.verify(getRequestedFor(urlEqualTo("/api/restaurants"))
                .withHeader("X-User-Id", absent())
                .withHeader("X-User-Role", absent()));
    }

    @Test
    void ownerReachesCabinetWithOwnIdentityAndCannotPretendToBeAnotherOwner() {
        client.put().uri("/api/cabinet/restaurants/3/stop-list/9")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(55L, "RESTAURANT_OWNER", "owner@example.com")))
                .header("X-User-Id", "56")
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/cabinet/restaurants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(7L, "USER", "kate@example.com")))
                .header("X-User-Role", "RESTAURANT_OWNER")
                .exchange()
                .expectStatus().isForbidden();

        SERVICES.verify(putRequestedFor(urlEqualTo("/api/cabinet/restaurants/3/stop-list/9"))
                .withHeader("X-User-Id", equalTo("55"))
                .withHeader("X-User-Role", equalTo("RESTAURANT_OWNER")));
        SERVICES.verify(0, getRequestedFor(urlEqualTo("/api/cabinet/restaurants")));
    }

    @Test
    void rejectsRequestWithoutToken() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();

        SERVICES.verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
    }

    @Test
    void unauthorizedAndForbiddenAreProblemDetails() {
        client.get().uri("/api/orders").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401).jsonPath("$.instance").isEqualTo("/api/orders");
        client.get().uri("/api/orders/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(7L, "USER", "kate@example.com")))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(403);
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @CsvSource({
            "GET,   /api/restaurants,            ANON,    200",
            "GET,   /api/restaurants/1/menu,     ANON,    200",
            "POST,  /api/restaurants,            ANON,    401",
            "POST,  /api/restaurants,            USER,    403",
            "POST,  /api/restaurants,            COURIER, 403",
            "POST,  /api/restaurants,            ADMIN,   200",
            "PUT,   /api/restaurants/1/image,    ANON,    401",
            "PUT,   /api/restaurants/1/image,    USER,    403",
            "PUT,   /api/restaurants/1/image,    ADMIN,   200",
            "PUT,   /api/restaurants/1/menu/2/image, USER,  403",
            "PUT,   /api/restaurants/1/menu/2/image, ADMIN, 200",
            "POST,  /api/admin/imports/meals,    ANON,    401",
            "POST,  /api/admin/imports/meals,    USER,    403",
            "POST,  /api/admin/imports/meals,    ADMIN,   200",
            "GET,   /api/admin/imports,          COURIER, 403",
            "GET,   /api/cabinet/restaurants,    ANON,    401",
            "GET,   /api/cabinet/restaurants,    USER,    403",
            "GET,   /api/cabinet/restaurants,    COURIER, 403",
            "GET,   /api/cabinet/restaurants,    ADMIN,   403",
            "GET,   /api/cabinet/restaurants,    RESTAURANT_OWNER, 200",
            "PUT,   /api/cabinet/restaurants/1/hours, USER, 403",
            "PUT,   /api/cabinet/restaurants/1/hours, RESTAURANT_OWNER, 200",
            "PUT,   /api/cabinet/restaurants/1/stop-list/2, ANON, 401",
            "PUT,   /api/cabinet/restaurants/1/stop-list/2, RESTAURANT_OWNER, 200",
            "DELETE, /api/cabinet/restaurants/1/menu/2, COURIER, 403",
            "DELETE, /api/cabinet/restaurants/1/menu/2, RESTAURANT_OWNER, 200",
            "POST,  /api/restaurants,            RESTAURANT_OWNER, 403",
            "PUT,   /api/restaurants/1/menu/2/image, RESTAURANT_OWNER, 403",
            "PUT,   /api/admin/restaurants/1/owner, ANON, 401",
            "PUT,   /api/admin/restaurants/1/owner, RESTAURANT_OWNER, 403",
            "PUT,   /api/admin/restaurants/1/owner, ADMIN, 200",
            "GET,   /api/admin/users,            ANON,    401",
            "PUT,   /api/admin/users/5/role,     USER,    403",
            "PUT,   /api/admin/users/5/role,     RESTAURANT_OWNER, 403",
            "PUT,   /api/admin/users/5/role,     ADMIN,   200",
            "GET,   /api/orders,                 ANON,    401",
            "GET,   /api/orders,                 USER,    200",
            "GET,   /api/orders/all,             USER,    403",
            "GET,   /api/orders/all,             COURIER, 403",
            "GET,   /api/orders/all,             ADMIN,   200",
            "PATCH, /api/orders/5/status,        USER,    403",
            "PATCH, /api/orders/5/status,        COURIER, 200",
            "PATCH, /api/orders/5/status,        ADMIN,   200",
            "POST,  /api/orders/5/cancel,        ANON,    401",
            "POST,  /api/orders/5/cancel,        USER,    200",
            "GET,   /api/cart,                   ANON,    401",
            "GET,   /api/cart,                   USER,    200",
            "POST,  /api/cart/items,             ANON,    401",
            "POST,  /api/cart/items,             USER,    200",
            "PUT,   /api/cart/items/10,          COURIER, 200",
            "DELETE, /api/cart,                  ANON,    401",
            "DELETE, /api/cart,                  USER,    200",
            "POST,  /api/cart/checkout,          ANON,    401",
            "POST,  /api/cart/checkout,          USER,    200",
            "GET,   /api/deliveries,             ANON,    401",
            "GET,   /api/deliveries,             USER,    403",
            "GET,   /api/deliveries,             COURIER, 200",
            "GET,   /api/deliveries,             ADMIN,   200",
            "GET,   /api/deliveries/5,           ANON,    401",
            "GET,   /api/deliveries/5,           USER,    200",
            "GET,   /api/deliveries/order/5,     USER,    200",
            "POST,  /api/deliveries,             USER,    403",
            "POST,  /api/deliveries,             COURIER, 200",
            "PATCH, /api/deliveries/5/status,    USER,    403",
            "PATCH, /api/deliveries/5/status,    COURIER, 200",
            "GET,   /actuator/env,               ANON,    401",
            "GET,   /actuator/env,               USER,    403",
            "GET,   /docs/orders/v3/api-docs,    ANON,    200",
            "GET,   /docs/users/v3/api-docs,     ANON,    200",
            "POST,  /docs/orders/v3/api-docs,    ANON,    401",
            "GET,   /docs/orders/api/orders,     ANON,    401",
            "GET,   /docs/orders/api/orders/all, USER,    404",
            "GET,   /docs/anything,              ANON,    401"
    })
    void roleMatrix(String method, String path, String role, int expectedStatus) {
        WebTestClient.RequestHeadersSpec<?> request = client.method(HttpMethod.valueOf(method)).uri(path);
        if (!"ANON".equals(role)) {
            request = request.header(HttpHeaders.AUTHORIZATION,
                    bearer(token(100L + role.length(), role, role.toLowerCase() + "@example.com")));
        }

        request.exchange().expectStatus().isEqualTo(expectedStatus);
    }

    @Test
    void docsRoutesReachOnlyApiDescriptionOfService() {
        client.get().uri("/docs/orders/v3/api-docs").exchange().expectStatus().isOk();
        client.get().uri("/docs/orders/api/orders").exchange().expectStatus().isUnauthorized();

        SERVICES.verify(1, getRequestedFor(urlEqualTo("/v3/api-docs")));
        SERVICES.verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
    }

    @Test
    void rejectsTokenWithoutSubjectUserIdOrExpiration() {
        JWTClaimsSet complete = claims(7L, "USER", "kate@example.com", Instant.now().plusSeconds(600), ISSUER);
        JWTClaimsSet noSubject = new JWTClaimsSet.Builder(complete).subject(null).build();
        JWTClaimsSet noUserId = new JWTClaimsSet.Builder(complete).claim("uid", null).build();
        JWTClaimsSet noExpiration = new JWTClaimsSet.Builder(complete).expirationTime(null).build();

        for (JWTClaimsSet incomplete : List.of(noSubject, noUserId, noExpiration)) {
            client.get().uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION,
                            bearer(sign(incomplete, JWSAlgorithm.RS256, signer(USER_SERVICE_KEY))))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        SERVICES.verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
    }

    @Test
    void rejectsTokenSignedWithForeignKeyEvenIfKeyIdMatches() {
        String forged = sign(claims(1L, "ADMIN", "attacker@example.com", Instant.now().plusSeconds(600), ISSUER),
                JWSAlgorithm.RS256, signer(ATTACKER_KEY));

        client.get().uri("/api/orders/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(forged))
                .exchange()
                .expectStatus().isUnauthorized();

        SERVICES.verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
    }

    @Test
    void rejectsTokenSignedWithSharedSecretInsteadOfRsaKey() throws JOSEException {
        String hmac = sign(claims(1L, "ADMIN", "attacker@example.com", Instant.now().plusSeconds(600), ISSUER),
                JWSAlgorithm.HS256, new MACSigner(USER_SERVICE_KEY.toRSAPublicKey().getEncoded()));

        client.get().uri("/api/orders/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(hmac))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = sign(claims(7L, "USER", "kate@example.com", Instant.now().minusSeconds(600), ISSUER),
                JWSAlgorithm.RS256, signer(USER_SERVICE_KEY));

        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(expired))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsTokenOfAnotherIssuer() {
        String foreign = sign(claims(7L, "USER", "kate@example.com", Instant.now().plusSeconds(600), "someone-else"),
                JWSAlgorithm.RS256, signer(USER_SERVICE_KEY));

        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(foreign))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void adminEndpointIsClosedForUserAndOpenForAdmin() {
        client.get().uri("/api/orders/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(7L, "USER", "kate@example.com")))
                .exchange()
                .expectStatus().isForbidden();
        client.get().uri("/api/orders/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(1L, "ADMIN", "admin@example.com")))
                .exchange()
                .expectStatus().isOk();

        SERVICES.verify(1, getRequestedFor(urlEqualTo("/api/orders/all")));
    }

    @Test
    void expiredAccessTokenDoesNotBlockRefresh() {
        String expired = sign(claims(7L, "USER", "kate@example.com", Instant.now().minusSeconds(600), ISSUER),
                JWSAlgorithm.RS256, signer(USER_SERVICE_KEY));

        client.post().uri("/api/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, bearer(expired))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\": \"opaque\"}")
                .exchange()
                .expectStatus().isOk();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/auth/refresh")).withHeader("X-User-Id", absent()));
    }

    private static String token(long userId, String role, String email) {
        return sign(claims(userId, role, email, Instant.now().plusSeconds(600), ISSUER),
                JWSAlgorithm.RS256, signer(USER_SERVICE_KEY));
    }

    private static JWTClaimsSet claims(long userId, String role, String email, Instant expiresAt, String issuer) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(email)
                .claim("uid", userId)
                .claim("role", role)
                .issueTime(Date.from(expiresAt.minusSeconds(900)))
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, JWSSigner signer) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(algorithm).keyID(USER_SERVICE_KEY.getKeyID()).build(), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JWSSigner signer(RSAKey key) {
        try {
            return new RSASSASigner(key);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
