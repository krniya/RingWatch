# Reconciliation Service

Scheduled model-drift detection. Not part of any live/user-facing request path: on a fixed
interval it samples past DECIDED transactions from the audit log and re-runs them through an
**isolated** copy of the scoring/decision pipeline, then diffs the new outcome against the
original. It reuses the same AI Risk Scoring and Decision Engine logic as the real pipeline, but
writes to none of their production tables or topics — a routine drift check can never corrupt live
fraud-detection state. See the root [README](../README.md#reconciliation) for the full flow
diagram.

Port: **8089**.

## Running it standalone

```bash
mvn -pl reconciliation-service -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `AUDIT_SERVICE_BASE_URL` | no | `http://localhost:8087` |
| `RECONCILIATION_INTERVAL_MS` | no | `21600000` (6h between scheduled runs) |
| `RECONCILIATION_MIN_AGE_MS` | no | `86400000` (1 day) |
| `RECONCILIATION_MAX_AGE_MS` | no | `604800000` (7 days) |
| `RECONCILIATION_SAMPLE_SIZE` | no | `50` (max decisions re-checked per run) |
| `RECONCILIATION_TOKEN_TTL_MS` | no | `60000` |

`RECONCILIATION_MIN_AGE_MS`/`MAX_AGE_MS` bound the lookback window — only decisions 1–7 days old
(by default) are eligible for re-checking, deliberately excluding both very fresh decisions (not
yet representative of "settled" behavior) and very old ones (outside the window audit-service is
expected to serve efficiently).

## What a scheduled run does

`ReconciliationScheduler` (`@Scheduled(fixedDelayString = "${ringwatch.reconciliation.schedule.interval-ms}")`)
runs this sequence on every fire:

1. Compute the lookback window `[now - maxAge, now - minAge]`.
2. `GET /audit?from=...&to=...` against `AUDIT_SERVICE_BASE_URL`, authenticated with a JWT the
   service **self-signs** (`ReconciliationTokenIssuer`, subject/username `reconciliation-service`,
   role `SYSTEM`) using the same shared `JWT_SECRET` every other service already trusts — no
   separate "service account" concept in auth-service is needed, since `common-lib`'s
   `JwtValidator` only requires a valid signature plus `subject`/`username`/`role` claims.
3. `TransactionSampler` picks up to `RECONCILIATION_SAMPLE_SIZE` DECIDED entries from the fetched
   window.
4. For each sampled entry, `ReconciliationRequestProducer` records the original outcome/risk
   score/reason in an in-memory `CorrelationStore` (keyed by a fresh `correlationId`) and publishes
   a `ReconciliationScoreRequest` to `transactions.reconciliation-scoring-requested`. A failure on
   one entry is caught and logged so it doesn't cost the rest of the run its coverage.
5. That request flows through the **isolated** re-scoring path: AI Risk Scoring Service consumes
   it, publishes to `transactions.reconciliation-scored`; Decision Engine consumes that, publishes
   a `ReconciliationDecisionResult` to `transactions.reconciliation-decided`.
6. `ReconciliationResultListener` consumes the result, looks up the matching `PendingReconciliation`
   by `correlationId`, and diffs it: **`drifted = (originalOutcome != newOutcome)`** — a pure
   outcome-mismatch check (APPROVE/FLAG/BLOCK), though the published result also carries both the
   original and new risk score and reason for context. It then publishes a
   `ReconciliationResultEvent` to `transactions.reconciled`, which audit-service records against
   the original transaction ID.

If a correlation ID has no pending entry when a result arrives (e.g. the service restarted
mid-run), the result is logged and dropped — that transaction simply gets re-sampled on a later
run, since sampling is over a rolling window rather than tracking "already reconciled" state.

If the whole run throws (e.g. audit-service is down), the run is abandoned and logged; the next
scheduled run retries from scratch. The one synchronous outbound call in this service —
`AuditServiceClient.fetchAuditEntries` — is wrapped in a Resilience4j `@Retry` (3 attempts, 1s
wait) for exactly this reason: a batch job's read failure just delays a sample, it never blocks
real-time fraud decisioning.

## Testing

```bash
mvn -pl reconciliation-service -am test
```

Unit tests cover `CorrelationStore` and `TransactionSampler` directly; `ReconciliationResultListenerIntegrationTest`
exercises the consume → diff → publish leg against `@EmbeddedKafka`.
