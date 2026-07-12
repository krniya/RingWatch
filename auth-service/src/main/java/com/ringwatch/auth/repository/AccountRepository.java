package com.ringwatch.auth.repository;

import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AnalystAccount, UUID> {

    Optional<AnalystAccount> findByUsername(String username);

    boolean existsByRole(Role role);
}
