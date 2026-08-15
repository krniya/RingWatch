# common-lib

The shared contract every other RingWatch module depends on: Kafka event schemas, topic name
constants, and JWT validation — so every service agrees on wire format and every service validates
analyst tokens the same way. A plain Maven `jar` module (`com.ringwatch:common-lib`), not a running
service — no port, no `@SpringBootApplication`.

## What's here

- **`event/`** — the Kafka event records that flow through the pipeline, one per hop:
  `TransactionRawEvent` (ingestion → `transactions.raw`), `EnrichedTransactionEvent`
  (enrichment → `transactions.enriched`), `ScoredTransactionEvent` (AI risk scoring →
  `transactions.scored`, carries a `ScoringMethod` — LLM or rule-based fallback), `FraudRingEvent`
  (fraud-ring detection → `transactions.ring-flagged`), `DecisionEvent` (decision engine →
  `transactions.decided`, carries a `DecisionOutcome` — APPROVE/FLAG/BLOCK),
  `DecisionOverriddenEvent` (decision engine → `transactions.overridden`), `AlertEvent` (
  notification service → `notifications.alerts`, carries an `AlertType`), and the reconciliation
  flow's parallel set — `ReconciliationScoreRequest`/`ReconciliationScoreResult`/
  `ReconciliationDecisionResult`/`ReconciliationResultEvent` — which reuses the same scoring/
  decision services on isolated topics rather than the production ones (see the root README's
  [Reconciliation](../README.md#reconciliation) section).
- **`kafka/Topics.java`** — the single source of truth for every topic name (`TRANSACTIONS_RAW`,
  `TRANSACTIONS_ENRICHED`, `TRANSACTIONS_SCORED`, `TRANSACTIONS_DECIDED`,
  `TRANSACTIONS_OVERRIDDEN`, `TRANSACTIONS_RING_FLAGGED`, the four
  `TRANSACTIONS_RECONCILIATION_*`/`TRANSACTIONS_RECONCILED` topics, `NOTIFICATIONS_ALERTS`) —
  producers and consumers reference these constants instead of hardcoding topic strings.
- **`security/`** — `JwtValidator`, constructed with the shared `JWT_SECRET` (HS256), parses and
  verifies a token's signature and returns an `AuthenticatedPrincipal(accountId, username, role)`
  from its claims. Every service that needs to know who's calling constructs its own
  `JwtValidator` from the same secret rather than calling out to auth-service — the "every service
  independently re-validates the JWT" model the root README describes.

## Building

Not published to a repository — every other module depends on it as an in-reactor Maven module, so
it must be built and installed locally before anything else compiles or runs:

```bash
mvn -pl common-lib install -DskipTests
```

If you change anything here, rebuild dependents with Maven's `-am` flag (e.g.
`mvn -pl auth-service -am test`) so they pick up the new class files instead of a stale locally
installed jar — see the root README's [Testing](../README.md#testing) section.
