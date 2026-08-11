package com.ringwatch.dashboardgateway.websocket;

import com.ringwatch.common.security.AuthenticatedPrincipal;
import com.ringwatch.common.security.JwtValidator;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * FR31: validates the analyst's JWT at the WebSocket handshake, read from a {@code ?token=} query
 * param rather than the {@code Authorization} header - the browser {@code WebSocket} constructor
 * has no header API, so the handshake is the one place in this system a JWT can't travel as a
 * bearer header. This service is the sole enforcer of auth for its route (the gateway's own
 * header-based JWT filter can't work here either, for the same reason), consistent with this
 * system's existing "every service independently re-validates the JWT itself" model.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";
    static final String PRINCIPAL_ATTRIBUTE = "principal";

    private final JwtValidator jwtValidator;

    public JwtHandshakeInterceptor(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(TOKEN_PARAM);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            AuthenticatedPrincipal principal = jwtValidator.validate(token);
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
