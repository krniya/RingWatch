package com.ringwatch.auth.controller.dto;

public record LoginResponse(
        String token,
        long expiresInSeconds
) {
}
