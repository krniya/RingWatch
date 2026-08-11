package com.ringwatch.dashboardgateway.config;

import com.ringwatch.common.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Split out from {@link WebSocketConfig} - that class's constructor depends on {@link
 * com.ringwatch.dashboardgateway.websocket.JwtHandshakeInterceptor}, which itself depends on this
 * bean; defining {@code JwtValidator} inside {@code WebSocketConfig} would make Spring need to
 * fully construct {@code WebSocketConfig} (to reach its {@code @Bean} method) before it can
 * satisfy {@code WebSocketConfig}'s own constructor - an unresolvable circular reference.
 */
@Configuration
public class JwtValidatorConfig {

    @Bean
    public JwtValidator jwtValidator(@Value("${ringwatch.jwt.secret}") String secret) {
        return new JwtValidator(secret);
    }
}
