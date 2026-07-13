package com.deliveryhub.user;

import com.deliveryhub.user.entity.RefreshToken;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.repository.RefreshTokenRepository;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class RefreshTokenRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-07-14T10:15:30Z");
    private static final Duration LIFETIME = Duration.ofDays(30);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void registerUser() {
        userId = userRepository.saveAndFlush(
                User.register("tokens@example.com", "hash", "Tokens", null, NOW)).getId();
    }

    @Test
    void findsTokenByHash() {
        RefreshToken token = refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "a".repeat(64), NOW, LIFETIME));

        assertThat(refreshTokenRepository.findByTokenHash("a".repeat(64))).get()
                .satisfies(found -> assertThat(found.getId()).isEqualTo(token.getId()));
        assertThat(refreshTokenRepository.findByTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void rotationChainIsStoredWithLinkToSuccessor() {
        RefreshToken first = refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "first", NOW, LIFETIME));

        RefreshToken second = first.rotate("second", NOW.plusSeconds(60), LIFETIME);
        refreshTokenRepository.saveAndFlush(second);

        RefreshToken reloaded = refreshTokenRepository.findByTokenHash("first").orElseThrow();
        assertThat(reloaded.getReplacedBy()).isEqualTo(second.getId());
        assertThat(reloaded.isRevoked()).isTrue();
    }

    @Test
    void revokesEveryLiveSuccessorOfReusedTokenButNotOtherFamilies() {
        RefreshToken first = refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "first", NOW, LIFETIME));
        RefreshToken second = refreshTokenRepository.saveAndFlush(first.rotate("second", NOW.plusSeconds(10), LIFETIME));
        refreshTokenRepository.saveAndFlush(second.rotate("third", NOW.plusSeconds(20), LIFETIME));
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "other-device", NOW, LIFETIME));
        refreshTokenRepository.flush();

        int revoked = refreshTokenRepository.revokeSuccessorsOf(first.getId(), NOW.plusSeconds(30));

        assertThat(revoked).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("third").orElseThrow().getRevokedAt())
                .isEqualTo(NOW.plusSeconds(30));
        assertThat(refreshTokenRepository.findByTokenHash("second").orElseThrow().getRevokedAt())
                .isEqualTo(NOW.plusSeconds(20));
        assertThat(refreshTokenRepository.findByTokenHash("other-device").orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void deletesOnlyExpiredTokens() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "expired", NOW.minus(Duration.ofDays(60)), LIFETIME));
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "live", NOW, LIFETIME));

        int removed = refreshTokenRepository.deleteExpiredBefore(NOW.minus(Duration.ofDays(7)));

        assertThat(removed).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("live")).isPresent();
    }

    @Test
    void revokedButUnexpiredTokenSurvivesCleanupHoweverLongAgoItWasRevoked() {
        RefreshToken rotated = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(userId, "rotated", NOW.minus(Duration.ofDays(20)), LIFETIME));
        refreshTokenRepository.saveAndFlush(rotated.rotate("successor", NOW.minus(Duration.ofDays(19)), LIFETIME));
        refreshTokenRepository.flush();

        int removed = refreshTokenRepository.deleteExpiredBefore(NOW.minus(Duration.ofDays(7)));

        assertThat(removed).isZero();
        assertThat(refreshTokenRepository.findByTokenHash("rotated")).get()
                .satisfies(token -> {
                    assertThat(token.isRevoked()).isTrue();
                    assertThat(token.getReplacedBy()).isNotNull();
                });
    }

    @Test
    void deletingSuccessorDoesNotBreakPredecessor() {
        RefreshToken first = refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "first", NOW, LIFETIME));
        RefreshToken second = refreshTokenRepository.saveAndFlush(first.rotate("second", NOW.plusSeconds(10), LIFETIME));
        second.revoke(NOW.plusSeconds(20));
        refreshTokenRepository.flush();

        refreshTokenRepository.delete(second);
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.findByTokenHash("first")).isPresent();
    }

    @Test
    void sameHashCannotBeStoredTwice() {
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "same", NOW, LIFETIME));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(RefreshToken.issue(userId, "same", NOW, LIFETIME)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
