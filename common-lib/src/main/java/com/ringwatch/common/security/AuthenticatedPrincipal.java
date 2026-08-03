package com.ringwatch.common.security;

public record AuthenticatedPrincipal(String accountId, String username, String role) {
}
