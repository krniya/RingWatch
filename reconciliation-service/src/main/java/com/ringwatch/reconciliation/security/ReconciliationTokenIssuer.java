package com.ringwatch.reconciliation.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FR24: self-mints a short-lived token so reconciliation-service (a batch job, not a live
 * user-facing request path) can call audit-service's protected {@code GET /audit} to sample past
 * decisions. Uses the same shared {@code ringwatch.jwt.secret} every service already trusts - no
 * new "service account" concept in auth-service is needed, since {@code common-lib}'s
 * {@code JwtValidator} only requires a valid signature plus {@code subject}/{@code username}/
 * {@code role} claims, with no issuer/audience or role-enum check.
 */
@Component
public class ReconciliationTokenIssuer {

    private final SecretKey signingKey;
    private final long ttlMs;

    public ReconciliationTokenIssuer(
            @Value("${ringwatch.jwt.secret}") String secret,
            @Value("${ringwatch.reconciliation.token-ttl-ms:60000}") long ttlMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMs = ttlMs;
    }

    public String issueToken() {
        Date now = new Date();
        return Jwts.builder()
                .subject("reconciliation-service")
                .claim("username", "reconciliation-service")
                .claim("role", "SYSTEM")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey)
                .compact();
    }
}
