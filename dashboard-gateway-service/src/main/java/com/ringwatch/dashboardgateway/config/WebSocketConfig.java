package com.ringwatch.dashboardgateway.config;

import com.ringwatch.dashboardgateway.websocket.AlertWebSocketHandler;
import com.ringwatch.dashboardgateway.websocket.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final String allowedOrigin;

    public WebSocketConfig(
            AlertWebSocketHandler alertWebSocketHandler,
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            @Value("${ringwatch.dashboard-gateway.allowed-origin}") String allowedOrigin) {
        this.alertWebSocketHandler = alertWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigin);
    }
}
