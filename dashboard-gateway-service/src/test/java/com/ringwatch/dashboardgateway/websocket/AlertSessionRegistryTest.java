package com.ringwatch.dashboardgateway.websocket;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class AlertSessionRegistryTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;

    @Mock private WebSocketSession sessionA;
    @Mock private WebSocketSession sessionB;

    @Test
    void broadcastSendsToEveryRegisteredOpenSession() throws Exception {
        AlertSessionRegistry registry = new AlertSessionRegistry();
        when(sessionA.isOpen()).thenReturn(true);
        when(sessionB.isOpen()).thenReturn(true);
        registry.register(sessionA);
        registry.register(sessionB);

        registry.broadcast("{\"alertId\":\"a-1\"}");

        // Sends happen off-thread now (each on its own executor task), so assertions must poll
        // rather than check synchronously right after broadcast() returns.
        verify(sessionA, timeout(ASYNC_TIMEOUT_MS)).sendMessage(new TextMessage("{\"alertId\":\"a-1\"}"));
        verify(sessionB, timeout(ASYNC_TIMEOUT_MS)).sendMessage(new TextMessage("{\"alertId\":\"a-1\"}"));
    }

    @Test
    void broadcastSkipsAClosedSession() throws Exception {
        AlertSessionRegistry registry = new AlertSessionRegistry();
        when(sessionA.isOpen()).thenReturn(false);
        registry.register(sessionA);

        registry.broadcast("{\"alertId\":\"a-1\"}");

        // Wait for the async task to actually reach and evaluate isOpen() before asserting
        // sendMessage was never reached - otherwise this could pass vacuously before it runs.
        verify(sessionA, timeout(ASYNC_TIMEOUT_MS)).isOpen();
        verify(sessionA, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unregisteredSessionNoLongerReceivesBroadcasts() throws Exception {
        AlertSessionRegistry registry = new AlertSessionRegistry();
        registry.register(sessionA);
        registry.unregister(sessionA);

        registry.broadcast("{\"alertId\":\"a-1\"}");

        // No task is ever submitted for an unregistered session, in sync or async code - safe to
        // assert immediately, unlike the closed-session case above.
        verify(sessionA, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }
}
