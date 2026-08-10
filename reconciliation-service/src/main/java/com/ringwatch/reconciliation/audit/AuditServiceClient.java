package com.ringwatch.reconciliation.audit;

import com.ringwatch.reconciliation.audit.dto.AuditLogEntryDto;
import com.ringwatch.reconciliation.security.ReconciliationTokenIssuer;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FR24: the one deliberate synchronous cross-service call in this system - a batch job's read
 * call to sample historical decisions, not part of any live/user-facing request path. If
 * audit-service is briefly unavailable, this run's sample fails and is retried on the next
 * scheduled run with zero impact on real-time fraud decisioning.
 */
@Component
public class AuditServiceClient {

    private final RestClient restClient;
    private final ReconciliationTokenIssuer tokenIssuer;

    public AuditServiceClient(
            @Value("${ringwatch.reconciliation.audit-service.base-url}") String baseUrl,
            RestClient.Builder restClientBuilder,
            ReconciliationTokenIssuer tokenIssuer) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.tokenIssuer = tokenIssuer;
    }

    @Retry(name = "auditServiceClient")
    public List<AuditLogEntryDto> fetchAuditEntries(Instant from, Instant to) {
        AuditLogEntryDto[] entries = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/audit")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer.issueToken())
                .retrieve()
                .body(AuditLogEntryDto[].class);
        return entries == null ? List.of() : List.of(entries);
    }
}
