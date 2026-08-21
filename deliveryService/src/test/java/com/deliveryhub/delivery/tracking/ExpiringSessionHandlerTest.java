package com.deliveryhub.delivery.tracking;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiringSessionHandlerTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-11-19T10:00:00Z");

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final ExpiringSessionHandler handler = new ExpiringSessionHandler(mock(WebSocketHandler.class), scheduler);

    @Test
    void closesSessionWhenTokenExpires() throws Exception {
        Map<String, Object> attributes = new HashMap<>(Map.of(GatewayIdentityHandshake.TOKEN_EXPIRES_AT, EXPIRES_AT));
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).schedule(any(Runnable.class), eq(EXPIRES_AT));

        handler.afterConnectionEstablished(session);
        ArgumentCaptor<Runnable> close = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(close.capture(), eq(EXPIRES_AT));
        close.getValue().run();

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
    }

    @Test
    void sessionWithoutExpiryIsNotScheduled() throws Exception {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
