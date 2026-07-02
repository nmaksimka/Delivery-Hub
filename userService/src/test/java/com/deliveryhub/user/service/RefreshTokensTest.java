package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.RefreshToken;
import com.deliveryhub.user.exception.InvalidRefreshTokenException;
import com.deliveryhub.user.exception.RefreshTokenReuseException;
import com.deliveryhub.user.repository.RefreshTokenRepository;
import com.deliveryhub.user.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokensTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:15:30Z");
    private static final Duration LIFETIME = Duration.ofDays(30);
    private static final long USER_ID = 7L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokens refreshTokens;

    @BeforeEach
    void setUp() {
        refreshTokens = new RefreshTokens(refreshTokenRepository,
                new JwtProperties("deliveryhub", Duration.ofMinutes(15), LIFETIME, null),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesOnlyHashOfIssuedToken() {
        String token = refreshTokens.issueFor(USER_ID);

        RefreshToken stored = savedToken();
        assertThat(token).hasSizeGreaterThanOrEqualTo(43).doesNotContain("=");
        assertThat(stored.getTokenHash()).hasSize(64).isEqualTo(RefreshTokens.hash(token)).isNotEqualTo(token);
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(LIFETIME));
    }

    @Test
    void issuesDifferentTokenEveryTime() {
        assertThat(refreshTokens.issueFor(USER_ID)).isNotEqualTo(refreshTokens.issueFor(USER_ID));
    }

    @Test
    void rotationReplacesPresentedTokenWithNewOne() {
        RefreshToken current = RefreshToken.issue(USER_ID, RefreshTokens.hash("presented"), NOW.minusSeconds(60), LIFETIME);
        when(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("presented"))).thenReturn(Optional.of(current));

        RefreshTokens.Rotation rotation = refreshTokens.rotate("presented");

        RefreshToken next = savedToken();
        assertThat(rotation.userId()).isEqualTo(USER_ID);
        assertThat(rotation.refreshToken()).isNotEqualTo("presented");
        assertThat(next.getTokenHash()).isEqualTo(RefreshTokens.hash(rotation.refreshToken()));
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedBy()).isEqualTo(next.getId());
    }

    @Test
    void presentingRotatedTokenAgainRevokesWholeFamily() {
        RefreshToken stolen = RefreshToken.issue(USER_ID, RefreshTokens.hash("stolen"), NOW.minusSeconds(60), LIFETIME);
        stolen.rotate("hash-of-successor", NOW.minusSeconds(30), LIFETIME);
        when(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("stolen"))).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> refreshTokens.rotate("stolen")).isInstanceOf(RefreshTokenReuseException.class);

        verify(refreshTokenRepository).revokeSuccessorsOf(stolen.getId(), NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rejectsExpiredToken() {
        RefreshToken expired = RefreshToken.issue(USER_ID, RefreshTokens.hash("old"), NOW.minus(LIFETIME), LIFETIME);
        when(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokens.rotate("old"))
                .isExactlyInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokens.rotate("made-up"))
                .isExactlyInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesTokenAndIgnoresUnknownOne() {
        RefreshToken current = RefreshToken.issue(USER_ID, RefreshTokens.hash("presented"), NOW.minusSeconds(60), LIFETIME);
        when(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("presented"))).thenReturn(Optional.of(current));
        when(refreshTokenRepository.findByTokenHash(RefreshTokens.hash("made-up"))).thenReturn(Optional.empty());

        refreshTokens.revoke("presented");
        refreshTokens.revoke("made-up");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
