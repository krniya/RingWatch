# Fraud Ring Detection Service

Consumes enriched transactions and clusters accounts that look organizationally linked — via
shared devices/IPs/fund transfers, or via circular fund movement through intermediaries — flagging
a fraud ring the moment either signal fires, explaining why in plain language via an LLM, and
persisting every detection for the dashboard's fraud-ring graph. Runs on **:8086**. See the root
[README](../README.md#architecture) for how it fits into the pipeline.

## Running it standalone

```bash
mvn -pl common-lib install -DskipTests
JWT_SECRET="a-dev-secret-at-least-32-bytes-long-for-hs256" \
mvn -pl fraud-ring-detection-service -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `JWT_SECRET` | yes | — |
| `ANTHROPIC_API_KEY` | no | empty → LLM calls fail fast, ring explanations fall back to a template |
| `ANTHROPIC_BASE_URL` | no | `https://api.anthropic.com` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | no | `http://localhost:4318/v1/traces` |

Needs `ringwatch_fraudring` (Postgres, `docker compose up -d` from the repo root) and Kafka
reachable at `localhost:9092`. `ringwatch.fraud-ring.min-cluster-size` (default `3`) is the minimum
cluster size before a shared-attribute ring is published — not currently exposed as an env var.

## The two detection algorithms

Every `EnrichedTransactionEvent` off `transactions.enriched` is run through both, independently —
a single transaction can trigger neither, one, or both.

### Account clustering — Union-Find

`graph/UnionFind.java` is a hand-rolled disjoint-set with path compression (`find`) and union by
rank (`union`), keyed by plain strings so it can cluster account IDs and synthetic attribute keys
in the same structure. `graph/AccountClusterGraph.java` wraps it: every transaction unions the
sender with the receiver (a transfer edge), with `device:<deviceId>`, and with `ip:<ipAddress>` —
so two accounts land in the same cluster if they're linked by *any chain* of shared
device/IP/transfer relationships, not just a single directly shared value.

Membership is derived on demand — scanning every known account ID and grouping by `find()` on the
transaction's sender, O(known accounts) per transaction. That's a deliberate demo-scale
simplification (a documented one, not an oversight): a system with many thousands of accounts
would want an incrementally-maintained root-to-members index instead of re-deriving membership
from scratch every call. A ring is published only the first time a cluster's size crosses
`min-cluster-size` *and* grows past its previously-published size — so an already-flagged cluster
doesn't re-publish on every subsequent transaction among its existing members, only on genuine
growth.

### Circular fund movement — BFS cycle detection

`graph/TransactionGraph.java` maintains a directed graph of `senderAccountId -> receiverAccountId`
edges. Before adding a new edge, it runs a BFS from the *receiver* looking for an existing path
back to the *sender* — if one exists, adding this edge would close a cycle
(`sender -> receiver -> ... -> sender`), i.e. money moving through a chain of accounts and coming
back to where it started. The check runs against the graph as it stood before this transaction, so
a self-transfer just records an edge without trivially "detecting" a cycle against itself.

## LLM explanation, with fallback

`service/RingExplainer.java` calls Claude (`claude-haiku-4-5` by default) with a one-or-two
sentence prompt describing the flagged cluster or cycle, wrapped in Resilience4j `@Retry` (2
attempts, 500ms) + `@CircuitBreaker` (count-based, 10-call window, 50% failure threshold, 30s open
state). The account IDs and attribute values sent to the model are explicitly framed as untrusted
data in the system prompt, not instructions, to prevent prompt injection from transaction
attributes an attacker could control (device ID, IP). On exhausted retries or an open circuit, it
falls back to a templated explanation (`"%d accounts (...) were flagged: %s"`) rather than blocking
ring publication on the LLM being available.

## REST API

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/fraud-rings` | JWT | All persisted ring detections, newest first — what the dashboard's fraud-rings graph page polls |

## Kafka

- **Consumes** `transactions.enriched` (`EnrichedTransactionEvent`)
- **Produces** `transactions.ring-flagged` (`FraudRingEvent`, keyed by `ringId`)

## Database

`fraud_ring_detections` (one row per ring *publication*, not a durable ring identity — a growing
cluster produces several rows over time, mirroring `audit_logs`' append-only philosophy):
`ring_id`, `shared_attributes`, `ai_explanation`, `detected_at`, `recorded_at`, plus a
`fraud_ring_detection_members` child table (`detection_id`, `account_id`).

## Testing

```bash
mvn -pl fraud-ring-detection-service -am test
```

Kafka interactions are tested against `spring-kafka-test`'s `@EmbeddedKafka`; the Claude call is
tested against WireMock (`wiremock-standalone`) rather than the real API.
