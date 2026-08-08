package com.ringwatch.notification.kafka;

import com.ringwatch.common.event.DecisionEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DecisionAlertListener {

    private final NotificationService notificationService;

    public DecisionAlertListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_DECIDED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.DecisionEvent")
    public void onTransactionDecided(DecisionEvent event) {
        notificationService.notifyOnDecision(event);
    }
}
