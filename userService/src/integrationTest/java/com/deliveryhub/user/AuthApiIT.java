package com.deliveryhub.user;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class AuthApiIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void registersUserAndLogsIn() {
        MvcTestResult registered = postJson("/api/auth/register", """
                {"email": "kate@example.com", "password": "secret123", "name": "Kate", "phone": "+79991234567"}
                """);

        assertThat(registered).hasStatus(201).bodyJson().extractingPath("$.role").isEqualTo("USER");
        assertThat(registered).bodyText().doesNotContain("secret123").doesNotContain("password");

        MvcTestResult loggedIn = postJson("/api/auth/login", """
                {"email": "kate@example.com", "password": "secret123"}
                """);

        assertThat(loggedIn).hasStatusOk().bodyJson().extractingPath("$.type").isEqualTo("Bearer");
        assertThat(loggedIn).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        assertThat(loggedIn).bodyJson().extractingPath("$.refreshToken").asString().isNotBlank();
        assertThat(loggedIn).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
    }

    @Test
    void accessTokenIsVerifiableWithPublishedJwks() throws Exception {
        String accessToken = JsonPath.read(bodyOf(login(register())), "$.accessToken");

        MvcTestResult jwks = mvc.get().uri("/.well-known/jwks.json").exchange();
        assertThat(jwks).hasStatusOk().bodyText().doesNotContain("\"d\"").doesNotContain("\"p\"");

        SignedJWT token = SignedJWT.parse(accessToken);
        RSAKey publicKey = JWKSet.parse(bodyOf(jwks)).getKeyByKeyId(token.getHeader().getKeyID()).toRSAKey();
        JWTClaimsSet claims = token.getJWTClaimsSet();

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(token.verify(new RSASSAVerifier(publicKey))).isTrue();
        assertThat(claims.getIssuer()).isEqualTo("deliveryhub");
        assertThat(claims.getStringClaim("role")).isEqualTo("USER");
        assertThat(claims.getLongClaim("uid")).isPositive();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void refreshRotatesTokenAndOldOneStopsWorking() {
        String firstRefresh = JsonPath.read(bodyOf(login(register())), "$.refreshToken");

        MvcTestResult refreshed = refresh(firstRefresh);

        assertThat(refreshed).hasStatusOk();
        String secondRefresh = JsonPath.read(bodyOf(refreshed), "$.refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        assertThat(refreshed).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        assertThat(refresh(secondRefresh)).hasStatusOk();
    }

    @Test
    void reuseOfRotatedTokenRevokesWholeFamily() {
        String stolen = JsonPath.read(bodyOf(login(register())), "$.refreshToken");
        String legitimate = JsonPath.read(bodyOf(refresh(stolen)), "$.refreshToken");

        MvcTestResult reuse = refresh(stolen);

        assertThat(reuse).hasStatus(401)
                .bodyJson().extractingPath("$.detail").isEqualTo("Refresh token is invalid or expired");
        assertThat(refresh(legitimate)).hasStatus(401);
    }

    @Test
    void reuseDoesNotTouchSessionsOnOtherDevices() {
        String email = register();
        String phone = JsonPath.read(bodyOf(login(email)), "$.refreshToken");
        String laptop = JsonPath.read(bodyOf(login(email)), "$.refreshToken");
        refresh(phone);

        assertThat(refresh(phone)).hasStatus(401);
        assertThat(refresh(laptop)).hasStatusOk();
    }

    @Test
    void logoutRevokesRefreshToken() {
        String refreshToken = JsonPath.read(bodyOf(login(register())), "$.refreshToken");

        assertThat(postJson("/api/auth/logout", refreshBody(refreshToken))).hasStatus(204);
        assertThat(postJson("/api/auth/logout", refreshBody(refreshToken))).hasStatus(204);
        assertThat(refresh(refreshToken)).hasStatus(401);
    }

    @Test
    void rejectsUnknownAndBlankRefreshToken() {
        assertThat(refresh("made-up-token")).hasStatus(401);
        assertThat(postJson("/api/auth/refresh", "{\"refreshToken\": \" \"}")).hasStatus(400);
    }

    @Test
    void rejectsSecondRegistrationWithSameEmail() {
        String body = """
                {"email": "twice@example.com", "password": "secret123", "name": "Twice"}
                """;
        postJson("/api/auth/register", body);

        assertThat(postJson("/api/auth/register", body)).hasStatus(409);
    }

    @Test
    void emailIsCaseInsensitive() {
        assertThat(postJson("/api/auth/register", """
                {"email": "Mixed.Case@Example.com", "password": "secret123", "name": "Mixed"}
                """)).hasStatus(201).bodyJson().extractingPath("$.email").isEqualTo("mixed.case@example.com");

        assertThat(postJson("/api/auth/register", """
                {"email": "mixed.case@example.COM", "password": "secret123", "name": "Mixed"}
                """)).hasStatus(409);
        assertThat(postJson("/api/auth/login", """
                {"email": "MIXED.CASE@EXAMPLE.COM", "password": "secret123"}
                """)).hasStatusOk();
    }

    @Test
    void concurrentRegistrationsWithSameEmailGiveOneConflict() throws Exception {
        String body = "{\"email\": \"race@example.com\", \"password\": \"secret123\", \"name\": \"Race\"}";
        int attempts = 6;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return postJson("/api/auth/register", body).getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).containsOnly(201, 409);
            assertThat(results).filteredOn(status -> status == 201).hasSize(1);
        }
    }

    @Test
    void swaggerDescriptionIsPublic() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }

    @Test
    void rejectsWrongPassword() {
        postJson("/api/auth/register", """
                {"email": "max@example.com", "password": "secret123", "name": "Max"}
                """);

        MvcTestResult result = postJson("/api/auth/login", """
                {"email": "max@example.com", "password": "wrong-password"}
                """);

        assertThat(result).hasStatus(401)
                .bodyJson().extractingPath("$.detail").isEqualTo("Invalid email or password");
    }

    @Test
    void validatesRegistrationRequest() {
        MvcTestResult result = postJson("/api/auth/register", """
                {"email": "not-an-email", "password": "short", "name": ""}
                """);

        assertThat(result).hasStatus(400).bodyJson().extractingPath("$.errors").asMap()
                .containsKeys("email", "password", "name");
    }

    private String register() {
        String email = UUID.randomUUID() + "@example.com";
        assertThat(postJson("/api/auth/register",
                "{\"email\": \"" + email + "\", \"password\": \"secret123\", \"name\": \"Test\"}")).hasStatus(201);
        return email;
    }

    private MvcTestResult login(String email) {
        return postJson("/api/auth/login", "{\"email\": \"" + email + "\", \"password\": \"secret123\"}");
    }

    private MvcTestResult refresh(String refreshToken) {
        return postJson("/api/auth/refresh", refreshBody(refreshToken));
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\": \"" + refreshToken + "\"}";
    }

    private MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
