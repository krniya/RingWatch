package com.ringwatch.reconciliation.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of audit-service's {@code AuditLogResponse} JSON shape - reconciliation-service
 * can't depend on audit-service's classes directly (it's a separate deployable service, not a
 * shared library).
 */
public record AuditLogEntryDto(
        UUID eventId,
        String transactionId,
        String eventType,
        JsonNode payload,
        String userId,
        Instant recordedAt
) {
}
