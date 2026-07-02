package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.RefreshToken;
import com.deliveryhub.user.exception.InvalidRefreshTokenException;
import com.deliveryhub.user.exception.RefreshTokenReuseException;
import com.deliveryhub.user.repository.RefreshTokenRepository;
import com.deliveryhub.user.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokens {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokens.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokens(RefreshTokenRepository refreshTokenRepository, JwtProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issueFor(Long userId) {
        String token = randomToken();
        refreshTokenRepository.save(
                RefreshToken.issue(userId, hash(token), clock.instant(), properties.refreshTokenTtl()));
        return token;
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public Rotation rotate(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is unknown"));

        if (current.isRevoked()) {
            int revoked = refreshTokenRepository.revokeSuccessorsOf(current.getId(), now);
            log.warn("Refresh token reuse detected for user {}, revoked {} tokens of the family",
                    current.getUserId(), revoked);
            throw new RefreshTokenReuseException(current.getUserId());
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token is expired");
        }

        String nextToken = randomToken();
        refreshTokenRepository.save(current.rotate(hash(nextToken), now, properties.refreshTokenTtl()));
        return new Rotation(current.getUserId(), nextToken);
    }

    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Rotation(Long userId, String refreshToken) {
    }
}
