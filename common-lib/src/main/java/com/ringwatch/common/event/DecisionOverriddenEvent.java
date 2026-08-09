package com.ringwatch.common.event;

import java.time.Instant;

/**
 * Published to {@code Topics.TRANSACTIONS_OVERRIDDEN} when an analyst overrides a decision
 * (FR22). Deliberately smaller than {@link DecisionEvent}: {@code decision-engine}'s own
 * {@code Decision} row never stores the sender/receiver/amount/enrichment fields {@link
 * DecisionEvent} carries, so there's nothing to re-hydrate them from at override time. The
 * dashboard's audit-trail fold only needs {@code outcome}/{@code reason} to update a
 * transaction's displayed status, plus {@code overriddenBy}/{@code overrideReason} for the
 * override-specific detail view - everything else about the transaction is already known from
 * its earlier CREATED/SCORED/DECIDED events.
 */
public record DecisionOverriddenEvent(
        String transactionId,
        DecisionOutcome outcome,
        String reason,
        String overriddenBy,
        String overrideReason,
        Instant overriddenAt
) {
}
