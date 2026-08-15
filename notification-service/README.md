# Notification Service

Consumes decision and fraud-ring detection events and turns them into analyst-facing alerts: an
email per FLAG/BLOCK decision or newly detected ring, plus a corresponding `AlertEvent` published
to Kafka for the dashboard's live in-app toasts. See the root [README](../README.md#architecture)
for how this fits into the wider pipeline.

Port `8088`. Consumer + outbound SMTP — no REST API, no database.

## Running it standalone

```bash
mvn -pl notification-service -am spring-boot:run
```

| Variable | Default | Notes |
|---|---|---|
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1025` | point at a local catcher like [MailHog](https://github.com/mailhog/MailHog) or Mailpit for dev |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | empty / empty | |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `false` / `false` | |
| `RINGWATCH_ALERT_RECIPIENTS` | `analyst@example.com` | comma-separated `To:` list |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | |

`JWT_SECRET` is **not** required — this service has no REST endpoints and never validates a token.

## What triggers what

- **`DecisionAlertListener`** consumes `transactions.decided`. `NotificationService.notifyOnDecision`
  ignores `APPROVE` outcomes and alerts on `FLAG`/`BLOCK` (`AlertType.TRANSACTION_FLAGGED` /
  `TRANSACTION_BLOCKED`), building an email whose body includes the transaction ID, sender/receiver
  accounts, amount/currency, outcome, and the decision's `reason`.
- **`RingAlertListener`** consumes `transactions.ring-flagged`. `NotificationService.notifyOnRing`
  alerts on every event unconditionally (`AlertType.RING_DETECTED`), building an email with the
  ring ID, shared attributes, member account IDs, and the AI-generated explanation.
- Both paths call the same `publish` method, which **always does both**: send the email via
  `EmailNotifier`, then publish an `AlertEvent` to `notifications.alerts` (which
  dashboard-gateway-service consumes and broadcasts to connected analyst browsers). The two
  channels can't diverge — there's no branch where one fires without the other.
- **No de-dup layer.** A documented, accepted characteristic (see the javadoc on
  `NotificationService`): fraud-ring-detection-service's BFS cycle detection can re-publish a fresh
  `FraudRingEvent` (new `ringId`) for what's semantically the same ring, and this service has no
  persistence to guard against a raw Kafka redelivery either. Both are accepted under FR32's
  best-effort framing rather than solved with added state here.

## Resilience

`EmailNotifier.send` wraps the SMTP call in a Resilience4j `@Retry` (`emailNotifier`: 3 attempts,
500ms wait) with a fallback (`logSendFailure`) that logs and returns — `send()` never throws, so a
struggling mail server can never block the Kafka consumer thread. No circuit breaker: FR32 scopes
the blanket "external calls" resilience requirement to AI calls; SMTP only needs retry + a
non-propagating fallback. The email subject is also stripped of `\r`/`\n`/control characters before
sending, closing off header injection from a crafted `transactionId` or `ringId`.

## Testing

```bash
mvn -pl notification-service -am test
```

Unit tests cover `NotificationService`/`EmailNotifier` directly; `NotificationListenersIntegrationTest`
exercises the Kafka listeners end-to-end against `@EmbeddedKafka` with GreenMail standing in for
the SMTP server.
