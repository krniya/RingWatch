package com.ringwatch.auth.controller.dto;

import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String username,
        Role role,
        Instant createdAt
) {
    public static AccountResponse from(AnalystAccount account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getRole(), account.getCreatedAt());
    }
}
