package com.ringwatch.notification.kafka;

import com.ringwatch.common.event.FraudRingEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RingAlertListener {

    private final NotificationService notificationService;

    public RingAlertListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = Topics.TRANSACTIONS_RING_FLAGGED,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.FraudRingEvent")
    public void onRingFlagged(FraudRingEvent event) {
        notificationService.notifyOnRing(event);
    }
}
