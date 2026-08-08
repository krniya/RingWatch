package com.ringwatch.audit.kafka;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.service.AuditLogService;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionCreatedAuditListener {

    private final AuditLogService auditLogService;

    public TransactionCreatedAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RAW,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.TransactionRawEvent")
    public void onTransactionCreated(TransactionRawEvent event) {
        auditLogService.record(event.transactionId(), AuditEventType.CREATED, event);
    }
}
