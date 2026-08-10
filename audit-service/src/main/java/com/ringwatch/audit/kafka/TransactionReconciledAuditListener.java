package com.ringwatch.audit.kafka;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.service.AuditLogService;
import com.ringwatch.common.event.ReconciliationResultEvent;
import com.ringwatch.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FR24: records a reconciliation-service drift check as its own RECONCILED audit entry, keyed by
 * the original transactionId - a separate topic/eventType (mirroring FR22's OVERRIDDEN) is what
 * lets this coexist with that transaction's existing DECIDED row despite {@code AuditLog}'s
 * unique constraint on {@code (transactionId, eventType)}. System-generated, not analyst-
 * attributed, so this uses the 3-arg {@code record(...)} overload (no {@code userId}).
 */
@Component
public class TransactionReconciledAuditListener {

    private final AuditLogService auditLogService;

    public TransactionReconciledAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RECONCILED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.ReconciliationResultEvent")
    public void onTransactionReconciled(ReconciliationResultEvent event) {
        auditLogService.record(event.originalTransactionId(), AuditEventType.RECONCILED, event);
    }
}
