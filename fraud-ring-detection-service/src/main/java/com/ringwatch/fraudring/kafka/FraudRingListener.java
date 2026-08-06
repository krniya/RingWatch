package com.ringwatch.fraudring.kafka;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.fraudring.service.FraudRingDetectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FraudRingListener {

    private final FraudRingDetectionService fraudRingDetectionService;

    public FraudRingListener(FraudRingDetectionService fraudRingDetectionService) {
        this.fraudRingDetectionService = fraudRingDetectionService;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_ENRICHED)
    public void onTransactionEnriched(EnrichedTransactionEvent event) {
        fraudRingDetectionService.process(event);
    }
}
