package com.ringwatch.gateway.config;

import com.ringwatch.common.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtValidator jwtValidator(@Value("${ringwatch.jwt.secret}") String secret) {
        return new JwtValidator(secret);
    }
}
