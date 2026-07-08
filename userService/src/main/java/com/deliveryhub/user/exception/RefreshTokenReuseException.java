package com.deliveryhub.user.exception;

public class RefreshTokenReuseException extends InvalidRefreshTokenException {

    public RefreshTokenReuseException(Long userId) {
        super("Revoked refresh token of user " + userId + " was presented again");
    }
}
