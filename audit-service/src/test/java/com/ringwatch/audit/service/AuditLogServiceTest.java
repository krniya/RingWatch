package com.ringwatch.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.model.AuditLog;
import com.ringwatch.audit.repository.AuditLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    private AuditLogService service;

    private record SamplePayload(String field) {
    }

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository, new ObjectMapper());
    }

    @Test
    void recordsANewEntryWithTheSerializedPayload() {
        service.record("tx-1", AuditEventType.CREATED, new SamplePayload("value"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo("tx-1");
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.CREATED);
        assertThat(captor.getValue().getPayload()).contains("\"field\":\"value\"");
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void recordsAnOverriddenEntryWithTheGivenUserId() {
        service.record("tx-1", AuditEventType.OVERRIDDEN, new SamplePayload("value"), "analyst-1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("analyst-1");
    }

    @Test
    void duplicateEntryIsSkippedWithoutThrowing() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.record("tx-1", AuditEventType.CREATED, new SamplePayload("value"));

        verify(repository, times(1)).save(any());
    }

    @Test
    void unserializablePayloadIsSkippedWithoutThrowing() {
        service.record("tx-1", AuditEventType.CREATED, new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        });

        verify(repository, never()).save(any());
    }

    @Test
    void findByTransactionIdDelegatesToTheOrderedRepositoryQuery() {
        service.findByTransactionId("tx-1");

        verify(repository, times(1)).findByTransactionIdOrderByRecordedAtAsc("tx-1");
    }

    @Test
    void searchDelegatesAllFiltersToTheRepository() {
        Instant from = Instant.now().minusSeconds(60);
        Instant to = Instant.now();

        service.search("analyst-1", from, to);

        verify(repository, times(1)).search("analyst-1", from, to);
    }
}
