package com.ringwatch.ingestion.controller;

import com.ringwatch.ingestion.controller.dto.CreateTransactionRequest;
import com.ringwatch.ingestion.controller.dto.TransactionResponse;
import com.ringwatch.ingestion.service.IngestionResult;
import com.ringwatch.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> submit(@Valid @RequestBody CreateTransactionRequest request) {
        IngestionResult result = ingestionService.submit(request);
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction(), result.duplicate()));
    }
}
