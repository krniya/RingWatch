package com.ringwatch.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedPrincipal validate(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedPrincipal(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
