# AI Risk Scoring Service

Consumes enriched transactions and produces a fraud risk score by calling Claude, falling back to
a deterministic rule-based scorer whenever the LLM call fails or the circuit is open. Sits between
Enrichment and the Decision Engine in the pipeline — see the [root README](../README.md#architecture)
for the full diagram.

## Running it

```bash
mvn -pl ai-risk-scoring-service -am spring-boot:run
```

| Variable | Required | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | No | empty — LLM calls fail immediately (no key), so every score comes from the rule-based fallback |
| `ANTHROPIC_BASE_URL` | No | `https://api.anthropic.com` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | No | `http://localhost:4318/v1/traces` |

Runs on **:8084**. The Claude model (`claude-haiku-4-5`) and an 8s request timeout are fixed in
`application.yml` (`ringwatch.ai.model` / `ringwatch.ai.timeout-seconds`), not overridable by env
var.

## What it does

`RiskScoringListener` consumes `transactions.enriched` (`EnrichedTransactionEvent`) and calls
`RiskScoringService.scoreAndPublish`, which delegates to `LlmRiskScorer` and publishes a
`ScoredTransactionEvent` (keyed by sender account ID) to `transactions.scored`.

`LlmRiskScorer` sends a system prompt + a templated user message (sender/receiver/amount/currency/
device/IP plus the sender's recent-transaction-count, average-amount, known-devices, known-IPs
history) to Claude, and requires a strict JSON reply: `{"riskScore": 0.0-1.0, "explanation": "..."}`.
Free-text fields (account IDs, device ID, IP) are sanitized (control characters stripped, capped at
128 chars) before being interpolated into the prompt as a prompt-injection mitigation, and the
system prompt itself instructs the model to treat those fields as untrusted data, not instructions.
A response missing either field, or that isn't valid JSON, is treated as a failure (not silently
defaulted) so it correctly triggers the Resilience4j fallback instead of publishing a false score.

**Rule-based fallback** (`RuleBasedRiskScorer`, `ScoringMethod.RULE_FALLBACK`) sums fixed weights
for whatever risk signals the enrichment context has:

| Signal | Weight |
|---|---|
| Sender has no prior transaction history | 0.10 |
| Amount > 5x the sender's average transaction | 0.35 |
| Device not among the sender's known devices | 0.30 |
| IP not among the sender's known IPs | 0.25 |

Weights sum and clamp to `[0, 1]`; each triggered signal is also surfaced as a sentence in the
explanation (e.g. `"Rule-based fallback: amount is more than 5x the sender's average transaction."`).
Historical fields are treated as optional (missing/null → empty/zero), since this scorer is also
the fallback for LLM failures caused by a malformed event — it can't itself throw on the same input
that just broke the AI path.

## Resilience

`LlmRiskScorer.score()` is wrapped `@Retry` (outer) around `@CircuitBreaker` (inner), both named
`llmRiskScorer`:

- **Circuit breaker**: count-based sliding window of 10 calls, minimum 5 calls before it can trip,
  opens above a 50% failure rate, stays open 30s, then allows 3 trial calls half-open.
- **Retry**: up to 2 attempts, 500ms between them.
- **Fallback**: `fallbackToRuleBased` is declared on `@Retry`, not `@CircuitBreaker` — Resilience4j
  nests retry as the outer decorator, so a fallback on the inner circuit breaker would swallow a
  failure before the outer retry ever saw one to retry, silently disabling `max-attempts`.

## Reconciliation path

`ReconciliationScoringListener` consumes `transactions.reconciliation-scoring-requested`
(`ReconciliationScoreRequest`) and calls `LlmRiskScorer` **directly** — bypassing
`RiskScoringService` on purpose, so a reconciliation re-score never publishes to the real
`transactions.scored` topic or anything downstream of it. The result is republished as a
`ReconciliationScoreResult` to `transactions.reconciliation-scored`, keyed by `correlationId` (which
stands in for the original transaction ID; the prompt itself never reads transaction/correlation
IDs, only the sender/receiver/device/IP/amount/history fields).

## Testing

```bash
mvn -pl ai-risk-scoring-service -am test
```

Unit tests cover `RuleBasedRiskScorer` and `LlmRiskScorer` (WireMock stubs the Anthropic API).
`RiskScoringListenerIntegrationTest` / `ReconciliationScoringListenerIntegrationTest` run against
`@EmbeddedKafka`.
