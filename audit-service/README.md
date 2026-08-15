# Audit Service

The immutable event log for RingWatch — every transaction lifecycle event (creation, scoring,
decision, analyst override, reconciliation) lands here against its transaction ID, so
`GET /audit/{transactionId}` always shows the complete history. It's also the read path behind the
dashboard: the live feed, review queue, and Overview trend charts all poll `GET /audit` under the
hood (via the gateway's `/audit/**` route), and it's the one cross-service call in the whole
platform made over synchronous REST instead of Kafka — reconciliation-service samples past
decisions through it using a self-signed JWT (see the [root README](../README.md#reconciliation)).

## Running it standalone

```bash
mvn -pl common-lib install -DskipTests   # if not already built
mvn -pl audit-service -am spring-boot:run
```

Requires Postgres and Kafka up (`docker compose up -d` from the repo root) and:

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |

Runs on **:8087**, against database `ringwatch_audit`.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/audit/{transactionId}` | Full event history for one transaction, oldest first |
| `GET` | `/audit?userId=&from=&to=` | Search across all transactions; every param is optional and filters are ANDed |

Both return a list of:

```json
{
  "eventId": "uuid",
  "transactionId": "tx-1",
  "eventType": "CREATED | SCORED | DECIDED | OVERRIDDEN | RECONCILED",
  "payload": { "...": "the original event, as JSON" },
  "userId": "only set on OVERRIDDEN entries — the analyst who made the call",
  "recordedAt": "instant"
}
```

`from`/`to` filter on `recordedAt` — when the event was recorded here, not the original event's own
timestamp. This matters for `RECONCILED` entries in particular: reconciliation-service intentionally
re-checks decisions 1–7 days old, so a `RECONCILED` row's `recordedAt` is recent even though the
transaction it's about may fall outside a short `from` window (the dashboard's Overview page relies
on exactly this to bucket reconciliation drift by *when the check ran*, not the original decision).

## Event types

One row is written per `(transactionId, eventType)` pair — a transaction accumulates multiple rows
over its life, one per lifecycle stage, but a unique DB constraint on that pair means the same event
redelivered by Kafka is silently skipped rather than duplicated (idempotency, not a data integrity
error — see `AuditLogService.record`). Rows are never updated or deleted after being written; "OVERRIDDEN"
and "RECONCILED" are recorded as their own new rows rather than mutating the original `DECIDED` row,
which is what actually makes the log immutable and preserves the full history instead of just the
latest state.

| Event type | Published by | Kafka topic |
|---|---|---|
| `CREATED` | ingestion-service | `transactions.raw` |
| `SCORED` | ai-risk-scoring-service | `transactions.scored` |
| `DECIDED` | decision-engine | `transactions.decided` |
| `OVERRIDDEN` | decision-engine (analyst override endpoint) | `transactions.overridden` |
| `RECONCILED` | reconciliation-service | `transactions.reconciled` |

## Testing

```bash
mvn -pl audit-service -am test
```

Covers the controller, service, repository (search filtering), and Kafka listeners
(`@EmbeddedKafka`) — including the redelivery-dedup path.
