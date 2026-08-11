package com.ringwatch.dashboardgateway.websocket;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * FR31: tracks every currently-connected analyst browser and fans a broadcast out to all of them.
 * No precedent elsewhere in this repo - every existing {@code @KafkaListener} does deterministic
 * work against a fixed downstream (DB write, republish), not a fan-out to an unbounded, changing
 * set of consumers. Broadcast is global (every connected analyst sees every alert) rather than
 * per-account-targeted - this system has no per-analyst targeting anywhere, even email alerts go
 * to one shared recipient list, so per-user filtering here would be new, unrequested scope.
 */
@Component
public class AlertSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AlertSessionRegistry.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    // One send per session runs here, off the single Kafka consumer thread that calls broadcast()
    // - without this, one slow/stalled browser's blocking sendMessage() would delay delivery to
    // every other connected analyst and delay the Kafka offset commit for that record.
    private final ExecutorService sendExecutor = Executors.newCachedThreadPool();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            sendExecutor.submit(() -> sendTo(session, message));
        }
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (Exception e) {
            // Catches IOException (a real send failure) and IllegalStateException (the session
            // closed between the isOpen() check and the send - a race, not an error worth
            // escalating) alike. Either way this must never propagate: broadcast() is called
            // directly from the Kafka listener thread, and an uncaught exception there would
            // trigger Spring Kafka's default retry, re-broadcasting the same alert to every other
            // already-delivered session.
            log.warn("Failed to send alert to session '{}'; leaving it registered for the next broadcast",
                    session.getId(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        sendExecutor.shutdown();
        try {
            if (!sendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sendExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
