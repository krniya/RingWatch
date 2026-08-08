package com.ringwatch.notification.service;

import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Wraps outbound SMTP delivery in Resilience4j {@code @Retry} per FR32 ("Notification delivery
 * failures ... shall not block the pipeline - delivery shall be async/fire-and-forget with retry,
 * and failures shall be logged rather than propagated back into the decision pipeline"). No
 * circuit breaker here - the NFR table's blanket resilience requirement is scoped to "external AI
 * calls," and FR32 only asks for retry + a non-propagating fallback for SMTP. {@code send()} never
 * throws: once retries are exhausted, {@code logSendFailure} logs and returns, so a struggling
 * mail server can never block or fail the Kafka consumer thread that calls this.
 */
@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final List<String> recipients;

    public EmailNotifier(
            JavaMailSender mailSender,
            @Value("${ringwatch.notification.from}") String fromAddress,
            @Value("${ringwatch.notification.recipients}") List<String> recipients) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.recipients = recipients.stream()
                .map(String::trim)
                .filter(recipient -> !recipient.isEmpty())
                .toList();
    }

    /**
     * Subject fields are built from event data (transactionId, account IDs, ...) that upstream
     * services only validate as non-blank, not as free of control characters - stripping them
     * here closes off email-header injection (a crafted transactionId containing "\r\nBcc: ...")
     * at this outbound boundary, the same way {@code RingExplainer.sanitize()} closes off prompt
     * injection at the LLM-call boundary.
     */
    @Retry(name = "emailNotifier", fallbackMethod = "logSendFailure")
    public void send(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipients.toArray(new String[0]));
        message.setSubject(subject.replaceAll("[\\r\\n\\p{Cntrl}]+", " ").trim());
        message.setText(body);
        mailSender.send(message);
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j as the @Retry fallback
    private void logSendFailure(String subject, String body, Throwable throwable) {
        log.error("Failed to send notification email '{}' after retries exhausted; the alert was "
                + "still published to Kafka.", subject, throwable);
    }
}
