package com.ringwatch.dashboardgateway.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** FR31: registers/unregisters a browser's session with {@link AlertSessionRegistry}. */
@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final AlertSessionRegistry registry;

    public AlertWebSocketHandler(AlertSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }
}
