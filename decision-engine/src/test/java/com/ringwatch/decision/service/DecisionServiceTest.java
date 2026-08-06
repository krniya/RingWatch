package com.ringwatch.decision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.ScoredTransactionEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.decision.model.Decision;
import com.ringwatch.decision.model.DecisionResult;
import com.ringwatch.decision.repository.DecisionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock private RuleBasedDecisionEngine decisionEngine;
    @Mock private DecisionRepository decisionRepository;
    @Mock private KafkaTemplate<String, DecisionEvent> kafkaTemplate;

    private DecisionService decisionService;

    private static ScoredTransactionEvent event(String transactionId) {
        return new ScoredTransactionEvent(
                transactionId, "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"),
                new BigDecimal("0.60"), "elevated risk", ScoringMethod.AI);
    }

    @Test
    void decideAndPublishPersistsAndPublishesNewDecision() {
        decisionService = new DecisionService(decisionEngine, decisionRepository, kafkaTemplate);
        ScoredTransactionEvent event = event("tx-1");
        when(decisionRepository.findByTransactionId("tx-1")).thenReturn(Optional.empty());
        when(decisionEngine.decide(event)).thenReturn(new DecisionResult(DecisionOutcome.FLAG, "flagged reason"));
        when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        DecisionEvent published = decisionService.decideAndPublish(event);

        assertThat(published.transactionId()).isEqualTo("tx-1");
        assertThat(published.outcome()).isEqualTo(DecisionOutcome.FLAG);
        assertThat(published.reason()).isEqualTo("flagged reason");
        assertThat(published.riskScore()).isEqualByComparingTo(event.riskScore());

        ArgumentCaptor<DecisionEvent> eventCaptor = ArgumentCaptor.forClass(DecisionEvent.class);
        verify(kafkaTemplate).send(eq(Topics.TRANSACTIONS_DECIDED), eq("sender-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(published);
    }

    @Test
    void decideAndPublishSkipsRedecisionForAlreadyDecidedTransaction() {
        decisionService = new DecisionService(decisionEngine, decisionRepository, kafkaTemplate);
        Decision existing = new Decision("tx-2", DecisionOutcome.BLOCK, "already blocked");
        when(decisionRepository.findByTransactionId("tx-2")).thenReturn(Optional.of(existing));

        DecisionEvent published = decisionService.decideAndPublish(event("tx-2"));

        assertThat(published.outcome()).isEqualTo(DecisionOutcome.BLOCK);
        assertThat(published.reason()).isEqualTo("already blocked");
        verify(decisionEngine, never()).decide(any());
        verify(decisionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void decideAndPublishRecoversFromConcurrentInsertRace() {
        decisionService = new DecisionService(decisionEngine, decisionRepository, kafkaTemplate);
        ScoredTransactionEvent event = event("tx-3");
        Decision racedWinner = new Decision("tx-3", DecisionOutcome.APPROVE, "decided by the other thread");
        when(decisionRepository.findByTransactionId("tx-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedWinner));
        when(decisionEngine.decide(event)).thenReturn(new DecisionResult(DecisionOutcome.FLAG, "this thread's reason"));
        when(decisionRepository.save(any(Decision.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        DecisionEvent published = decisionService.decideAndPublish(event);

        assertThat(published.outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(published.reason()).isEqualTo("decided by the other thread");
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void decideAndPublishDoesNotThrowWhenKafkaPublishFails() {
        decisionService = new DecisionService(decisionEngine, decisionRepository, kafkaTemplate);
        ScoredTransactionEvent event = event("tx-4");
        when(decisionRepository.findByTransactionId("tx-4")).thenReturn(Optional.empty());
        when(decisionEngine.decide(event)).thenReturn(new DecisionResult(DecisionOutcome.APPROVE, "fine"));
        when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));
        CompletableFuture<SendResult<String, DecisionEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        DecisionEvent published = decisionService.decideAndPublish(event);

        assertThat(published.outcome()).isEqualTo(DecisionOutcome.APPROVE);
    }
}
