package com.ringwatch.audit.controller.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.model.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID eventId,
        String transactionId,
        AuditEventType eventType,
        JsonNode payload,
        String userId,
        Instant recordedAt) {

    /**
     * {@code auditLog.getPayload()} is always valid JSON - {@code AuditLogService} only ever
     * writes it via {@code ObjectMapper.writeValueAsString} before persisting - so a parse
     * failure here would mean a broken invariant elsewhere, not a normal case to fall back from.
     */
    public static AuditLogResponse from(AuditLog auditLog, ObjectMapper objectMapper) throws JsonProcessingException {
        return new AuditLogResponse(
                auditLog.getId(), auditLog.getTransactionId(), auditLog.getEventType(),
                objectMapper.readTree(auditLog.getPayload()), auditLog.getUserId(), auditLog.getRecordedAt());
    }
}
