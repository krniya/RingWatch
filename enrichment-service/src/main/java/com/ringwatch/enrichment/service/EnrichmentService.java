package com.ringwatch.enrichment.service;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.enrichment.cache.LruCache;
import com.ringwatch.enrichment.model.AccountHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final LruCache<String, AccountHistory> accountHistoryCache;
    private final KafkaTemplate<String, EnrichedTransactionEvent> kafkaTemplate;

    public EnrichmentService(
            @Value("${ringwatch.enrichment.cache-capacity}") int cacheCapacity,
            KafkaTemplate<String, EnrichedTransactionEvent> kafkaTemplate) {
        this.accountHistoryCache = new LruCache<>(cacheCapacity);
        this.kafkaTemplate = kafkaTemplate;
    }

    public EnrichedTransactionEvent enrich(TransactionRawEvent event) {
        AccountHistory history = accountHistoryCache.computeAndPut(
                event.senderAccountId(),
                AccountHistory.empty(),
                prior -> prior.withTransaction(event.deviceId(), event.ipAddress(), event.amount()));

        EnrichedTransactionEvent enriched = new EnrichedTransactionEvent(
                event.transactionId(),
                event.senderAccountId(),
                event.receiverAccountId(),
                event.amount(),
                event.currency(),
                event.deviceId(),
                event.ipAddress(),
                event.timestamp(),
                history.recentTxnCount(),
                history.averageAmount(),
                history.knownDevices(),
                history.knownIps());

        kafkaTemplate.send(Topics.TRANSACTIONS_ENRICHED, event.senderAccountId(), enriched)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish enriched transaction '{}' to {}",
                                event.transactionId(), Topics.TRANSACTIONS_ENRICHED, ex);
                    }
                });

        return enriched;
    }
}
