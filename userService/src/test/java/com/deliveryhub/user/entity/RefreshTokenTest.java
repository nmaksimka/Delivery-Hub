package com.deliveryhub.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:15:30Z");
    private static final Duration LIFETIME = Duration.ofDays(30);

    @Test
    void issuedTokenIsActiveUntilItExpires() {
        RefreshToken token = RefreshToken.issue(7L, "hash", NOW, LIFETIME);

        assertThat(token.isActive(NOW)).isTrue();
        assertThat(token.isActive(NOW.plus(LIFETIME).minusSeconds(1))).isTrue();
        assertThat(token.isExpired(NOW.plus(LIFETIME))).isTrue();
        assertThat(token.isActive(NOW.plus(LIFETIME))).isFalse();
    }

    @Test
    void rotationRevokesTokenAndLinksItToSuccessor() {
        RefreshToken token = RefreshToken.issue(7L, "hash", NOW, LIFETIME);

        RefreshToken next = token.rotate("next-hash", NOW.plusSeconds(60), LIFETIME);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(token.getReplacedBy()).isEqualTo(next.getId());
        assertThat(next.getUserId()).isEqualTo(7L);
        assertThat(next.getTokenHash()).isEqualTo("next-hash");
        assertThat(next.getExpiresAt()).isEqualTo(NOW.plusSeconds(60).plus(LIFETIME));
        assertThat(next.isActive(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void revokedTokenCannotBeRotatedAgain() {
        RefreshToken token = RefreshToken.issue(7L, "hash", NOW, LIFETIME);
        token.rotate("next-hash", NOW, LIFETIME);

        assertThatThrownBy(() -> token.rotate("another-hash", NOW, LIFETIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiredTokenCannotBeRotated() {
        RefreshToken token = RefreshToken.issue(7L, "hash", NOW, LIFETIME);

        assertThatThrownBy(() -> token.rotate("next-hash", NOW.plus(LIFETIME), LIFETIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void repeatedRevokeKeepsFirstTimestamp() {
        RefreshToken token = RefreshToken.issue(7L, "hash", NOW, LIFETIME);

        token.revoke(NOW.plusSeconds(1));
        token.revoke(NOW.plusSeconds(99));

        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(token.getReplacedBy()).isNull();
    }
}
