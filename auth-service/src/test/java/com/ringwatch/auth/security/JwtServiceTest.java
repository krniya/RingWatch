package com.ringwatch.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("test-only-secret-key-at-least-32-bytes-long-for-hs256", 3_600_000L);

    @Test
    void generatesTokenThatRoundTripsClaims() {
        AnalystAccount account = new AnalystAccount("jdoe", "hashed", Role.ANALYST);
        account.setId(UUID.randomUUID());

        String token = jwtService.generateToken(account);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(account.getId().toString());
        assertThat(claims.get("username", String.class)).isEqualTo("jdoe");
        assertThat(claims.get("role", String.class)).isEqualTo("ANALYST");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void exposesExpirationInSeconds() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600L);
    }
}
