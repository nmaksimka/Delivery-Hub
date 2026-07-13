package com.deliveryhub.user.cleanup;

import com.deliveryhub.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final CleanupProperties properties;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokenRepository,
                               CleanupProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval:1h}", initialDelayString = "${app.cleanup.initial-delay:1m}")
    @Transactional
    public void removeExpiredTokens() {
        int removed = refreshTokenRepository.deleteExpiredBefore(
                clock.instant().minus(properties.refreshTokenRetention()));
        if (removed > 0) {
            log.info("Removed {} expired refresh tokens", removed);
        }
    }
}
