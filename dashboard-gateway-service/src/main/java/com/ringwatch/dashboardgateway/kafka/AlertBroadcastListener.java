package com.ringwatch.dashboardgateway.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.common.event.AlertEvent;
import com.ringwatch.common.kafka.Topics;
import com.ringwatch.dashboardgateway.websocket.AlertSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * FR31: consumes every alert notification-service publishes and broadcasts it, as-is, to every
 * connected analyst browser. Deliberately doesn't pre-compute a display "tone" server-side - the
 * existing outcome-to-tone mapping precedent ({@code OUTCOME_TOAST_TONE} in the dashboard's
 * {@code useOverrideDecision.js}) already lives client-side, so mirroring it keeps that mapping in
 * one place/style rather than splitting it across the stack.
 */
@Component
public class AlertBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcastListener.class);

    private final AlertSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public AlertBroadcastListener(AlertSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = Topics.NOTIFICATIONS_ALERTS,
            properties = "spring.json.value.default.type:com.ringwatch.common.event.AlertEvent")
    public void onAlert(AlertEvent event) {
        try {
            registry.broadcast(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alert '{}' for broadcast", event.alertId(), e);
        }
    }
}
