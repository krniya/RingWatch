# Decision Engine

Combines an AI risk score with fraud-ring membership into a final `APPROVE`/`FLAG`/`BLOCK`
decision, prioritizing high-risk transactions under load via a hand-rolled min-heap, and exposes
the analyst override endpoint. Port `8085`. REST + Kafka consumer/producer + Postgres — see the
root [README](../README.md#architecture) for how it fits into the wider pipeline.

## Running it standalone

```bash
mvn -pl common-lib install -DskipTests   # if not already built
JWT_SECRET="a-dev-secret-at-least-32-bytes-long-for-hs256" \
mvn -pl decision-engine -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | no | `http://localhost:4318/v1/traces` |

Needs Postgres reachable at `jdbc:postgresql://localhost:5432/ringwatch_decisions`
(`ringwatch`/`ringwatch`, provisioned by the root `docker-compose.yml`) and Kafka at
`localhost:9092`.

## Decision logic

`RuleBasedDecisionEngine` applies risk-score thresholds first, then a ring-membership escalation:

1. `riskScore >= block-threshold` (default `0.75`) → `BLOCK`
2. else `riskScore >= flag-threshold` (default `0.40`) → `FLAG`
3. else → `APPROVE`

If either the sender or receiver account is a known fraud-ring member (tracked in
`RingMembershipRegistry`, kept current by `RingFlagListener` consuming
`transactions.ring-flagged`), the outcome is escalated one step — `APPROVE`→`FLAG` or
`FLAG`→`BLOCK` — never de-escalated, and a `BLOCK` from the thresholds alone always stands.
Thresholds are configurable via `ringwatch.decision.block-threshold` /
`ringwatch.decision.flag-threshold` in `application.yml`.

**Known, accepted limitation:** `transactions.ring-flagged` and `transactions.scored` are
independent consumers with no ordering guarantee between them, so a transaction can still be
decided (and APPROVEd) before a ring-flag event for the same account is consumed. There's no
mechanism to revisit an already-published decision once the flag later arrives.

## The min-heap: prioritizing under load

`MinHeap<E>` (`priority/MinHeap.java`) is a hand-rolled array-backed binary min-heap — `insert`/
`extractMin` in O(log n) via sift-up/sift-down — the DSA centerpiece for this service (see the root
README's [DSA centerpieces](../README.md#dsa-centerpieces) table).

`DecisionPriorityQueue` wraps one `MinHeap<ScoredTransactionEvent>`, ordered by risk score
descending, as a bounded (`ringwatch.decision.queue-capacity`, default `1000`), blocking buffer
between two threads:

- **Producer** — `DecisionListener`, a `@KafkaListener` on `transactions.scored`, enqueues every
  scored event and nothing else (it doesn't decide inline).
- **Consumer** — `DecisionWorker`, a dedicated background thread started in `@PostConstruct`,
  continuously drains the queue highest-risk-first and calls `DecisionService.decideAndPublish`.

Under a load spike, the queue fills faster than the worker drains it, so the highest-risk
transactions are always decided first instead of strictly in arrival order. `enqueue` blocks while
the queue is at capacity, which doubles as backpressure — it pauses the Kafka listener thread
until the worker catches up.

**Accepted tradeoff:** the Kafka offset is committed as soon as an event is *enqueued*, not once
it's actually decided, so a crash between enqueue and the worker draining it loses that one
in-flight decision. Closing that gap would require a durable pending-decision marker per message
before acking — real follow-up work, not addressed here.

## Analyst override

```
POST /transactions/{transactionId}/override
Authorization: Bearer <jwt>
Content-Type: application/json

{ "outcome": "APPROVE" | "FLAG" | "BLOCK", "reason": "..." }
```

Looks up the existing `Decision` row by `transactionId` (404 if none exists), applies the new
outcome/reason, persists it, and publishes a `DecisionOverriddenEvent` to
`transactions.overridden`. The original decision's rationale isn't lost — it stays in the
immutable `DECIDED` audit event in audit-service, independent of this row's current-state fields.

## Data owned

Postgres table `decisions` (`ringwatch_decisions` DB): `id`, `transaction_id` (unique),
`outcome`, `reason`, `overridden_by`, `override_reason`, `created_at`. `DecisionService` is
idempotent on `transaction_id` — redelivery of the same scored event returns the existing decision
without re-persisting or re-publishing.

## Kafka topics

| Direction | Topic | Event | Listener/publisher |
|---|---|---|---|
| Consumes | `transactions.scored` | `ScoredTransactionEvent` | `DecisionListener` → priority queue |
| Consumes | `transactions.ring-flagged` | `FraudRingEvent` | `RingFlagListener` → `RingMembershipRegistry` |
| Publishes | `transactions.decided` | `DecisionEvent` | `DecisionService.decideAndPublish` |
| Publishes | `transactions.overridden` | `DecisionOverriddenEvent` | `DecisionService.override` |

**Reconciliation flow** (see the root README's [Reconciliation](../README.md#reconciliation)
section): `ReconciliationDecisionListener` consumes `transactions.reconciliation-scored`
(`ReconciliationScoreResult`) and publishes `transactions.reconciliation-decided`
(`ReconciliationDecisionResult`). It calls `RuleBasedDecisionEngine` directly, bypassing both
`DecisionService` (never touches the `decisions` table or the real `transactions.decided` topic)
and the priority queue (no latency SLA to justify competing with live traffic for queue capacity).

## Testing

```bash
mvn -pl decision-engine -am test
```

Unit tests for `MinHeap`, `DecisionPriorityQueue`, `RuleBasedDecisionEngine`, and
`RingMembershipRegistry`; `@EmbeddedKafka` integration tests for the listeners and the override
controller (`DecisionControllerIntegrationTest`, `DecisionListenerIntegrationTest`,
`RingFlagListenerIntegrationTest`, `ReconciliationDecisionListenerIntegrationTest`). The `-am` flag
rebuilds `common-lib` from source when it's changed instead of using a stale local copy.
