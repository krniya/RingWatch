package com.ringwatch.audit.repository;

import com.ringwatch.audit.model.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTransactionIdOrderByRecordedAtAsc(String transactionId);

    // The CASTs are load-bearing on real Postgres (invisible on H2, which these tests run
    // against): once PgJDBC promotes this query to a server-side prepared statement (after
    // ~5 executions with the same SQL text), Postgres resolves every parameter's type from the
    // SQL text alone, before any value is bound. A placeholder whose only appearance is inside
    // an "IS NULL" check gives Postgres no type to infer - it doesn't matter whether the bound
    // value is null or not - and the query fails with "could not determine data type of
    // parameter $N". Casting each IS-NULL-checked placeholder gives it an explicit type.
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (CAST(:userId AS string) IS NULL OR a.userId = :userId)
              AND (CAST(:from AS timestamp) IS NULL OR a.recordedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR a.recordedAt <= :to)
            ORDER BY a.recordedAt ASC
            """)
    List<AuditLog> search(@Param("userId") String userId, @Param("from") Instant from, @Param("to") Instant to);
}
