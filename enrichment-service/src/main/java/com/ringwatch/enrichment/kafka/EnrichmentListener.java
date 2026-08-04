package com.ringwatch.enrichment.kafka;

import com.ringwatch.common.event.TransactionRawEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.enrichment.service.EnrichmentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EnrichmentListener {

    private final EnrichmentService enrichmentService;

    public EnrichmentListener(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_RAW)
    public void onTransactionRaw(TransactionRawEvent event) {
        enrichmentService.enrich(event);
    }
}
