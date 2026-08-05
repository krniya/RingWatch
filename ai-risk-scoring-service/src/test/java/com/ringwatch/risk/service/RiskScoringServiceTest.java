package com.ringwatch.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.EnrichedTransactionEvent;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.risk.model.ScoringResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class RiskScoringServiceTest {

    private LlmRiskScorer llmRiskScorer;
    private KafkaTemplate<String, ScoredTransactionEvent> kafkaTemplate;
    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        llmRiskScorer = mock(LlmRiskScorer.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new RiskScoringService(llmRiskScorer, kafkaTemplate);

        SendResult<String, ScoredTransactionEvent> sendResult =
                new SendResult<>(new ProducerRecord<>(Topics.TRANSACTIONS_SCORED, "k", null), mock(RecordMetadata.class));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(ScoredTransactionEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    private static EnrichedTransactionEvent enrichedEvent() {
        return new EnrichedTransactionEvent(
                "tx-1", "sender-1", "receiver-1", new BigDecimal("100.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("90.00"),
                Set.of("device-1"), Set.of("10.0.0.1"));
    }

    @Test
    void scoreAndPublishCombinesEnrichedFieldsWithScoringResult() {
        EnrichedTransactionEvent event = enrichedEvent();
        when(llmRiskScorer.score(event)).thenReturn(
                new ScoringResult(new BigDecimal("0.75"), "looks risky", ScoringMethod.AI));

        ScoredTransactionEvent scored = service.scoreAndPublish(event);

        assertThat(scored.transactionId()).isEqualTo("tx-1");
        assertThat(scored.senderAccountId()).isEqualTo("sender-1");
        assertThat(scored.recentTxnCount()).isEqualTo(3);
        assertThat(scored.riskScore()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(scored.explanation()).isEqualTo("looks risky");
        assertThat(scored.scoringMethod()).isEqualTo(ScoringMethod.AI);
    }

    @Test
    void scoreAndPublishSendsToScoredTopicKeyedBySenderAccountId() {
        EnrichedTransactionEvent event = enrichedEvent();
        when(llmRiskScorer.score(event)).thenReturn(
                new ScoringResult(BigDecimal.ZERO, "fine", ScoringMethod.RULE_FALLBACK));

        service.scoreAndPublish(event);

        verify(kafkaTemplate).send(eq(Topics.TRANSACTIONS_SCORED), eq("sender-1"), any(ScoredTransactionEvent.class));
    }

    @Test
    void scoreAndPublishDoesNotThrowWhenKafkaPublishFails() {
        EnrichedTransactionEvent event = enrichedEvent();
        when(llmRiskScorer.score(event)).thenReturn(
                new ScoringResult(BigDecimal.ZERO, "fine", ScoringMethod.RULE_FALLBACK));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(ScoredTransactionEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        assertThat(service.scoreAndPublish(event)).isNotNull();
    }
}
