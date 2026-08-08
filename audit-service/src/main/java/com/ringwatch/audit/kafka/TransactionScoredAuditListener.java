package com.ringwatch.audit.kafka;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.service.AuditLogService;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionScoredAuditListener {

    private final AuditLogService auditLogService;

    public TransactionScoredAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_SCORED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.ScoredTransactionEvent")
    public void onTransactionScored(ScoredTransactionEvent event) {
        auditLogService.record(event.transactionId(), AuditEventType.SCORED, event);
    }
}
