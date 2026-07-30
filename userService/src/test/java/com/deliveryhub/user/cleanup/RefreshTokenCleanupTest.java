package com.deliveryhub.user.cleanup;

import com.deliveryhub.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshTokenCleanupTest {

    private static final Instant NOW = Instant.parse("2026-07-20T03:00:00Z");

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);

    @Test
    void removesTokensExpiredLongerThanRetention() {
        RefreshTokenCleanup cleanup = new RefreshTokenCleanup(refreshTokenRepository,
                new CleanupProperties(Duration.ofDays(7)), Clock.fixed(NOW, ZoneOffset.UTC));

        cleanup.removeExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredBefore(Instant.parse("2026-07-13T03:00:00Z"));
    }
}
