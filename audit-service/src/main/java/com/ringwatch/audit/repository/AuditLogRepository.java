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

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:from IS NULL OR a.recordedAt >= :from)
              AND (:to IS NULL OR a.recordedAt <= :to)
            ORDER BY a.recordedAt ASC
            """)
    List<AuditLog> search(@Param("userId") String userId, @Param("from") Instant from, @Param("to") Instant to);
}
