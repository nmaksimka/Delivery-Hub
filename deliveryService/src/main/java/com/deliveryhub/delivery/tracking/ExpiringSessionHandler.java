package com.deliveryhub.delivery.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class ExpiringSessionHandler extends WebSocketHandlerDecorator {

    private static final Logger log = LoggerFactory.getLogger(ExpiringSessionHandler.class);
    private static final CloseStatus TOKEN_EXPIRED = CloseStatus.POLICY_VIOLATION.withReason("Token expired");

    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> expirations = new ConcurrentHashMap<>();

    public ExpiringSessionHandler(WebSocketHandler delegate, TaskScheduler scheduler) {
        super(delegate);
        this.scheduler = scheduler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getAttributes().get(GatewayIdentityHandshake.TOKEN_EXPIRES_AT) instanceof Instant expiresAt) {
            expirations.put(session.getId(), scheduler.schedule(() -> close(session), expiresAt));
        }
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        ScheduledFuture<?> expiration = expirations.remove(session.getId());
        if (expiration != null) {
            expiration.cancel(false);
        }
        super.afterConnectionClosed(session, closeStatus);
    }

    private static void close(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(TOKEN_EXPIRED);
            }
        } catch (IOException e) {
            log.debug("Tracking socket {} was already gone when its token expired", session.getId());
        }
    }
}
