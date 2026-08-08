package com.ringwatch.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringwatch.audit.model.AuditEventType;
import com.ringwatch.audit.model.AuditLog;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository repository;

    @Test
    void findByTransactionIdReturnsAllEntriesOrderedByRecordedAt() {
        repository.save(new AuditLog("tx-1", AuditEventType.CREATED, "{}", null));
        repository.save(new AuditLog("tx-1", AuditEventType.SCORED, "{}", null));
        repository.save(new AuditLog("tx-2", AuditEventType.CREATED, "{}", null));

        List<AuditLog> entries = repository.findByTransactionIdOrderByRecordedAtAsc("tx-1");

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(AuditLog::getEventType)
                .containsExactly(AuditEventType.CREATED, AuditEventType.SCORED);
    }

    @Test
    void unknownTransactionIdReturnsEmptyList() {
        assertThat(repository.findByTransactionIdOrderByRecordedAtAsc("no-such-tx")).isEmpty();
    }

    @Test
    void aGenuineDuplicateTransactionIdAndEventTypePairViolatesTheUniqueConstraint() {
        repository.saveAndFlush(new AuditLog("tx-dup", AuditEventType.CREATED, "{}", null));

        assertThatThrownBy(() -> repository.saveAndFlush(new AuditLog("tx-dup", AuditEventType.CREATED, "{}", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchWithNoFiltersReturnsEverything() {
        repository.save(new AuditLog("tx-1", AuditEventType.CREATED, "{}", null));
        repository.save(new AuditLog("tx-2", AuditEventType.OVERRIDDEN, "{}", "analyst-1"));

        assertThat(repository.search(null, null, null)).hasSize(2);
    }

    @Test
    void searchFiltersByUserId() {
        repository.save(new AuditLog("tx-1", AuditEventType.CREATED, "{}", null));
        repository.save(new AuditLog("tx-2", AuditEventType.OVERRIDDEN, "{}", "analyst-1"));
        repository.save(new AuditLog("tx-3", AuditEventType.OVERRIDDEN, "{}", "analyst-2"));

        List<AuditLog> results = repository.search("analyst-1", null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTransactionId()).isEqualTo("tx-2");
    }

    @Test
    void searchFiltersByTimeRange() {
        AuditLog inRange = repository.saveAndFlush(new AuditLog("tx-1", AuditEventType.CREATED, "{}", null));
        Instant from = inRange.getRecordedAt().minus(1, ChronoUnit.HOURS);
        Instant to = inRange.getRecordedAt().plus(1, ChronoUnit.HOURS);

        assertThat(repository.search(null, from, to)).hasSize(1);
        assertThat(repository.search(null, to, to.plus(1, ChronoUnit.HOURS))).isEmpty();
    }
}
