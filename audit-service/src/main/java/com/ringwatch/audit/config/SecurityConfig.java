package com.ringwatch.audit.config;

import com.ringwatch.audit.security.JwtAuthFilter;
import com.ringwatch.common.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtValidator jwtValidator(@Value("${ringwatch.jwt.secret}") String secret) {
        return new JwtValidator(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtValidator jwtValidator) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtValidator), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
