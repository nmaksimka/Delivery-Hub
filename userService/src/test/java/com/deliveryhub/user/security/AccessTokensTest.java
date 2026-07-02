package com.deliveryhub.user.security;

import com.deliveryhub.user.UserFixtures;
import com.deliveryhub.user.config.SigningKeyConfig;
import com.deliveryhub.user.entity.Role;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokensTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:15:30Z");
    private static final JwtProperties PROPERTIES =
            new JwtProperties("deliveryhub", Duration.ofMinutes(15), Duration.ofDays(30), null);

    private final SigningKeyConfig config = new SigningKeyConfig();
    private final RSAKey signingKey = config.signingKey(PROPERTIES);
    private final AccessTokens accessTokens =
            new AccessTokens(config.jwtEncoder(signingKey), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signsTokenWithRs256AndPutsKeyIdIntoHeader() throws Exception {
        SignedJWT token = SignedJWT.parse(accessTokens.issue(UserFixtures.persistedUser(7L, "kate@example.com", Role.USER)));

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(token.getHeader().getKeyID()).isEqualTo(signingKey.getKeyID());
        assertThat(token.verify(new RSASSAVerifier(signingKey.toRSAPublicKey()))).isTrue();
    }

    @Test
    void carriesIdentityAndShortLifetime() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(
                accessTokens.issue(UserFixtures.persistedUser(7L, "admin@example.com", Role.ADMIN))).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("deliveryhub");
        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.getLongClaim(AccessTokens.CLAIM_USER_ID)).isEqualTo(7L);
        assertThat(claims.getStringClaim(AccessTokens.CLAIM_ROLE)).isEqualTo("ADMIN");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    void tokenSignedByAnotherKeyDoesNotVerify() throws Exception {
        RSAKey otherKey = config.signingKey(PROPERTIES);
        SignedJWT token = SignedJWT.parse(accessTokens.issue(UserFixtures.persistedUser(7L, "kate@example.com", Role.USER)));

        assertThat(token.verify(new RSASSAVerifier(otherKey.toRSAPublicKey()))).isFalse();
    }

    @Test
    void everyTokenHasItsOwnId() throws Exception {
        var user = UserFixtures.persistedUser(7L, "kate@example.com", Role.USER);

        assertThat(SignedJWT.parse(accessTokens.issue(user)).getJWTClaimsSet().getJWTID())
                .isNotEqualTo(SignedJWT.parse(accessTokens.issue(user)).getJWTClaimsSet().getJWTID());
    }
}
