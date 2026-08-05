package com.ringwatch.risk.kafka;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.risk.service.RiskScoringService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskScoringListener {

    private final RiskScoringService riskScoringService;

    public RiskScoringListener(RiskScoringService riskScoringService) {
        this.riskScoringService = riskScoringService;
    }

    @KafkaListener(topics = Topics.TRANSACTIONS_ENRICHED)
    public void onTransactionEnriched(EnrichedTransactionEvent event) {
        riskScoringService.scoreAndPublish(event);
    }
}
