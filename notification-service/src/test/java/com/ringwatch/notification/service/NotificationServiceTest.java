package com.ringwatch.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.common.event.AlertEvent;
import com.ringwatch.common.event.AlertType;
import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.event.DecisionOutcome;
import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.event.ScoringMethod;
import com.ringwatch.common.kafka.Topics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private EmailNotifier emailNotifier;
    @Mock private KafkaTemplate<String, AlertEvent> kafkaTemplate;

    private NotificationService notificationService;

    private static DecisionEvent decisionEvent(DecisionOutcome outcome) {
        return new DecisionEvent(
                "tx-1", "sender-1", "receiver-1", new BigDecimal("500.00"), "USD",
                "device-1", "10.0.0.1", Instant.now(), 3, new BigDecimal("50.00"),
                Set.of("device-1"), Set.of("10.0.0.1"), new BigDecimal("0.80"),
                "some explanation", ScoringMethod.AI, outcome, "some reason", Instant.now());
    }

    private static FraudRingEvent ringEvent() {
        return new FraudRingEvent(
                "ring-1", Set.of("A", "B", "C"), "shared device", "ai explanation", Instant.now());
    }

    @Test
    void approvedDecisionsDoNotTriggerAnyNotification() {
        notificationService = new NotificationService(emailNotifier, kafkaTemplate);

        notificationService.notifyOnDecision(decisionEvent(DecisionOutcome.APPROVE));

        verify(emailNotifier, never()).send(any(), any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void flaggedDecisionSendsEmailAndPublishesATransactionFlaggedAlert() {
        notificationService = new NotificationService(emailNotifier, kafkaTemplate);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        notificationService.notifyOnDecision(decisionEvent(DecisionOutcome.FLAG));

        verify(emailNotifier, times(1)).send(any(), any());
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(Topics.NOTIFICATIONS_ALERTS), eq("tx-1"), captor.capture());
        assertThat(captor.getValue().alertType()).isEqualTo(AlertType.TRANSACTION_FLAGGED);
        assertThat(captor.getValue().transactionId()).isEqualTo("tx-1");
        assertThat(captor.getValue().ringId()).isNull();
    }

    @Test
    void blockedDecisionSendsEmailAndPublishesATransactionBlockedAlert() {
        notificationService = new NotificationService(emailNotifier, kafkaTemplate);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        notificationService.notifyOnDecision(decisionEvent(DecisionOutcome.BLOCK));

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(Topics.NOTIFICATIONS_ALERTS), eq("tx-1"), captor.capture());
        assertThat(captor.getValue().alertType()).isEqualTo(AlertType.TRANSACTION_BLOCKED);
    }

    @Test
    void ringDetectionSendsEmailAndPublishesARingDetectedAlertKeyedByRingId() {
        notificationService = new NotificationService(emailNotifier, kafkaTemplate);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        notificationService.notifyOnRing(ringEvent());

        verify(emailNotifier, times(1)).send(any(), any());
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(Topics.NOTIFICATIONS_ALERTS), eq("ring-1"), captor.capture());
        assertThat(captor.getValue().alertType()).isEqualTo(AlertType.RING_DETECTED);
        assertThat(captor.getValue().ringId()).isEqualTo("ring-1");
        assertThat(captor.getValue().transactionId()).isNull();
        assertThat(captor.getValue().message()).contains("A", "B", "C", "ai explanation");
    }

    @Test
    void kafkaPublishFailureDoesNotThrow() {
        notificationService = new NotificationService(emailNotifier, kafkaTemplate);
        CompletableFuture<SendResult<String, AlertEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        notificationService.notifyOnDecision(decisionEvent(DecisionOutcome.FLAG));

        verify(emailNotifier, times(1)).send(any(), any());
    }
}
