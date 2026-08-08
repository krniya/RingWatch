package com.ringwatch.audit.kafka;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.service.AuditLogService;
import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionDecidedAuditListener {

    private final AuditLogService auditLogService;

    public TransactionDecidedAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_DECIDED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.DecisionEvent")
    public void onTransactionDecided(DecisionEvent event) {
        auditLogService.record(event.transactionId(), AuditEventType.DECIDED, event);
    }
}
