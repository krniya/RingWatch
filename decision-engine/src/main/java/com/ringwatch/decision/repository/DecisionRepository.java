package com.ringwatch.decision.repository;

import com.ringwatch.decision.model.Decision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByTransactionId(String transactionId);
}
