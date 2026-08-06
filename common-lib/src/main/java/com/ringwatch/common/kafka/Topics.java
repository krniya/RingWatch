package com.ringwatch.common.kafka;

public final class Topics {

    public static final String TRANSACTIONS_RAW = "transactions.raw";
    public static final String TRANSACTIONS_ENRICHED = "transactions.enriched";
    public static final String TRANSACTIONS_SCORED = "transactions.scored";
    public static final String TRANSACTIONS_DECIDED = "transactions.decided";
    public static final String TRANSACTIONS_RING_FLAGGED = "transactions.ring-flagged";

    private Topics() {
    }
}
