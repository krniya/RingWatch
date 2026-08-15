# Ingestion Service

The first hop in the RingWatch pipeline: accepts a transaction from the API Gateway, deduplicates
it against Postgres, and publishes it to Kafka for the rest of the pipeline to pick up. See the
root [README](../README.md#architecture) for how this fits into the full flow.

## Running it standalone

```bash
mvn -pl common-lib install -DskipTests   # build the shared library first, if not already built
JWT_SECRET="a-dev-secret-at-least-32-bytes-long-for-hs256" \
mvn -pl ingestion-service -am spring-boot:run
```

Runs on **:8082**. Requires Postgres (`ringwatch_ingestion` database) and Kafka reachable at
`localhost:5432`/`localhost:9092` — see the root [README](../README.md#1-start-the-infrastructure)
for `docker compose up -d`. `JWT_SECRET` is the only required env var (used to validate the
`Authorization: Bearer` JWT on every request, same shared secret every other service trusts).

## API

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/transactions` | `CreateTransactionRequest` (see below) | `201 Created` with the stored transaction, or `200 OK` if it was a duplicate |

`CreateTransactionRequest`: `transactionId`, `senderAccountId`, `receiverAccountId`, `amount`
(> 0), `currency` (3-letter ISO 4217 code), `deviceId`, `ipAddress`, `timestamp` — all required and
validated with Bean Validation (`jakarta.validation`) before touching the database.

## Deduplication

`IngestionService.submit` looks up the incoming `transactionId` in the `transactions` table
(unique-constrained) before doing anything else:

- **Not found** — inserts a new row, then publishes to Kafka. A `DataIntegrityViolationException`
  on insert (a concurrent duplicate request racing the lookup) is treated the same as a normal
  duplicate: the already-inserted row is re-fetched and returned instead of erroring.
- **Found, same payload** — returns the existing stored transaction with `duplicate: true` in the
  response. Nothing is re-published to Kafka — this is what makes retried/at-least-once delivery
  from an upstream caller safe.
- **Found, different payload** — the mismatch is logged as a warning and the *original* stored
  transaction is kept; the new payload is discarded rather than overwriting history.

Dedup is Postgres-backed (not in-memory), so it's correct across restarts and multiple ingestion
instances, at the cost of one DB round-trip per request.

## Kafka

Publishes one `TransactionRawEvent` (from `common-lib`) per newly accepted transaction to the
`transactions.raw` topic (`Topics.TRANSACTIONS_RAW`), keyed by `transactionId`. Duplicates are
never re-published. Downstream, this topic is consumed by the Enrichment Service and the Audit
Service (see the root README's architecture diagram).

## Testing

```bash
mvn -pl ingestion-service -am test
```

Covered by a `@SpringBootTest` + `MockMvc` controller test (with `KafkaTemplate` mocked via
`@MockBean`, so no broker is needed) and a plain unit test of `IngestionService`'s dedup logic.
