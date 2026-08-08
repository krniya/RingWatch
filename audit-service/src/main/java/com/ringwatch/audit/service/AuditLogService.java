package com.ringwatch.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.model.AuditLog;
import com.ringwatch.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Idempotent on (transactionId, eventType) the same way {@code IngestionService}/
 * {@code DecisionService} are on transactionId alone: a redelivered Kafka message for an event
 * already recorded is silently skipped rather than producing a duplicate audit row.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(String transactionId, AuditEventType eventType, Object eventPayload) {
        record(transactionId, eventType, eventPayload, null);
    }

    public void record(String transactionId, AuditEventType eventType, Object eventPayload, String userId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {} audit payload for transaction '{}'; entry not recorded.",
                    eventType, transactionId, e);
            return;
        }

        try {
            repository.save(new AuditLog(transactionId, eventType, payload, userId));
        } catch (DataIntegrityViolationException e) {
            log.debug("A {} audit entry for transaction '{}' was already recorded; skipping duplicate.",
                    eventType, transactionId);
        }
    }

    public List<AuditLog> findByTransactionId(String transactionId) {
        return repository.findByTransactionIdOrderByRecordedAtAsc(transactionId);
    }

    public List<AuditLog> search(String userId, Instant from, Instant to) {
        return repository.search(userId, from, to);
    }
}
