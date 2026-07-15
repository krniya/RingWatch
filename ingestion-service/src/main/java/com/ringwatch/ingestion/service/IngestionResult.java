package com.ringwatch.ingestion.service;

import com.ringwatch.ingestion.model.Transaction;

public record IngestionResult(Transaction transaction, boolean duplicate) {
}
