package com.ringwatch.audit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.audit.controller.dto.AuditLogResponse;
import com.ringwatch.audit.model.AuditLog;
import com.ringwatch.audit.service.AuditLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditController(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/audit/{transactionId}")
    public List<AuditLogResponse> byTransactionId(@PathVariable("transactionId") String transactionId)
            throws JsonProcessingException {
        return toResponses(auditLogService.findByTransactionId(transactionId));
    }

    @GetMapping("/audit")
    public List<AuditLogResponse> search(
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) throws JsonProcessingException {
        return toResponses(auditLogService.search(userId, from, to));
    }

    private List<AuditLogResponse> toResponses(List<AuditLog> entries) throws JsonProcessingException {
        List<AuditLogResponse> responses = new ArrayList<>(entries.size());
        for (AuditLog entry : entries) {
            responses.add(AuditLogResponse.from(entry, objectMapper));
        }
        return responses;
    }
}
