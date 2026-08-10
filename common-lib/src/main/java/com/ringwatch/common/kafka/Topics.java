package com.ringwatch.common.kafka;

public final class Topics {

    public static final String TRANSACTIONS_RAW = "transactions.raw";
    public static final String TRANSACTIONS_ENRICHED = "transactions.enriched";
    public static final String TRANSACTIONS_SCORED = "transactions.scored";
    public static final String TRANSACTIONS_DECIDED = "transactions.decided";
    public static final String TRANSACTIONS_OVERRIDDEN = "transactions.overridden";
    public static final String TRANSACTIONS_RING_FLAGGED = "transactions.ring-flagged";
    public static final String TRANSACTIONS_RECONCILIATION_SCORING_REQUESTED = "transactions.reconciliation-scoring-requested";
    public static final String TRANSACTIONS_RECONCILIATION_SCORED = "transactions.reconciliation-scored";
    public static final String TRANSACTIONS_RECONCILIATION_DECIDED = "transactions.reconciliation-decided";
    public static final String TRANSACTIONS_RECONCILED = "transactions.reconciled";
    public static final String NOTIFICATIONS_ALERTS = "notifications.alerts";

    private Topics() {
    }
}
