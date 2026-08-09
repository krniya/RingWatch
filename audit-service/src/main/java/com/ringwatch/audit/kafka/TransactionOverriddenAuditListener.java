package com.ringwatch.audit.kafka;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.service.AuditLogService;
import com.ringwatch.common.event.DecisionOverriddenEvent;
import com.ringwatch.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FR22: records the analyst's override as its own OVERRIDDEN audit entry, distinct from the
 * original DECIDED row. A separate topic (rather than reusing transactions.decided) is what
 * makes this possible - {@code AuditLog}'s unique constraint on {@code (transactionId,
 * eventType)} means a same-eventType republish would otherwise be silently dropped as a
 * duplicate.
 */
@Component
public class TransactionOverriddenAuditListener {

    private final AuditLogService auditLogService;

    public TransactionOverriddenAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_OVERRIDDEN,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.DecisionOverriddenEvent")
    public void onTransactionOverridden(DecisionOverriddenEvent event) {
        auditLogService.record(event.transactionId(), AuditEventType.OVERRIDDEN, event, event.overriddenBy());
    }
}
