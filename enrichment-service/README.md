# Enrichment Service

Consumes raw transactions off Kafka and attaches historical account context — recent transaction
count, average amount, and the set of known devices/IPs for the sender — before publishing an
enriched event for the scoring and fraud-ring stages downstream. Part of the RingWatch pipeline;
see the [root README](../README.md#architecture) for the full picture.

- **Port:** 8083
- **Type:** Consumer + Producer (no REST API, no database)

## Running it standalone

```bash
mvn -pl common-lib install -DskipTests   # if not already built
mvn -pl enrichment-service -am spring-boot:run
```

No required environment variables beyond what's shared infra-wide (Kafka at `localhost:9092`,
OTLP tracing endpoint). See the [root README](../README.md#2-set-environment-variables) for the
full env var table.

## What it does

`EnrichmentListener` consumes `TransactionRawEvent` off `transactions.raw`. For each event,
`EnrichmentService` looks up the sender account's `AccountHistory` in an in-memory LRU cache,
folds the new transaction into it (`AccountHistory.withTransaction`), and publishes an
`EnrichedTransactionEvent` to `transactions.enriched` (keyed by `senderAccountId`) carrying the
account history *as it stood before this transaction* — `recentTxnCount`, `averageAmount`,
`knownDevices`, `knownIps` — alongside the original transaction fields. That "before" history is
what lets the AI Risk Scoring Service reason about whether a transaction looks anomalous relative
to the account's own pattern (new device, amount far from average, etc.).

The cache is process-local and unbounded across restarts (an LRU eviction, not a TTL) — it only
ever reflects what this instance has consumed since it last started, and a `senderAccountId` not
yet seen simply gets `AccountHistory.empty()`.

## LRU cache

`cache/LruCache.java` is the hand-rolled DSA centerpiece for this service (see the [root README's
DSA table](../README.md#dsa-centerpieces)): a `HashMap<K, Node<K,V>>` for O(1) lookup plus an
intrusive doubly linked list for O(1) recency reordering and eviction, capacity fixed at
construction (`ringwatch.enrichment.cache-capacity`, default **10,000** entries, keyed by
`senderAccountId`). `get`/`put` are `synchronized` under a single monitor covering the whole
structure — not per-key — because a `get` on any key mutates shared list state (moves that node to
the front), so a finer-grained lock couldn't safely protect the list ordering alone.

`computeAndPut(key, defaultValue, remapper)` exists because the natural read-modify-write use
here (read current history, fold in the new transaction, write it back) isn't atomic if composed
from separate `get()`/`put()` calls — two listener threads could both read the same stale history
and the second `put` would silently clobber the first's update. Java's intrinsic locks are
reentrant, so `computeAndPut` can call the synchronized `get`/`put` internally while still holding
the lock for the whole read-modify-write sequence.

On eviction (`size() > capacity`), the least-recently-used node (`tail.prev`) is dropped — that
account's history starts over from `AccountHistory.empty()` the next time a transaction for it
arrives.

## Testing

```bash
mvn -pl enrichment-service -am test
```

Covers `LruCache` directly (capacity/eviction/recency ordering), `AccountHistory`'s folding logic,
`EnrichmentService`, and a Kafka integration test (`EnrichmentListenerIntegrationTest`) against
`@EmbeddedKafka` exercising the full raw → enriched round trip.
