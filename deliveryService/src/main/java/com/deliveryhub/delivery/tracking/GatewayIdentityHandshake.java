package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.security.InternalTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class GatewayIdentityHandshake implements HandshakeInterceptor {

    public static final String CURRENT_USER = "currentUser";
    public static final String TOKEN_EXPIRES_AT = "tokenExpiresAt";
    private static final String HEADER_TOKEN_EXP = "X-User-Token-Exp";

    private static final Logger log = LoggerFactory.getLogger(GatewayIdentityHandshake.class);

    private final byte[] expectedToken;

    public GatewayIdentityHandshake(@Value("${app.internal-token}") String internalToken) {
        Assert.hasText(internalToken, "Internal token is required");
        this.expectedToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        HttpHeaders headers = request.getHeaders();
        if (!hasValidToken(headers.getFirst(InternalTokenFilter.HEADER_INTERNAL_TOKEN))) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Optional<CurrentUser> user = userOf(headers);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(CURRENT_USER, user.get());
        tokenExpiry(headers).ifPresent(expiresAt -> attributes.put(TOKEN_EXPIRES_AT, expiresAt));
        log.debug("Tracking socket opened by user {} with role {}", user.get().id(), user.get().role());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private boolean hasValidToken(String token) {
        return token != null && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Instant> tokenExpiry(HttpHeaders headers) {
        String exp = headers.getFirst(HEADER_TOKEN_EXP);
        if (exp == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(exp)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<CurrentUser> userOf(HttpHeaders headers) {
        String id = headers.getFirst("X-User-Id");
        String role = headers.getFirst("X-User-Role");
        if (id == null || role == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CurrentUser(Long.parseLong(id), role));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
