package com.ringwatch.auth.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import com.ringwatch.auth.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountSeederTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void skipsSeedingWhenAnAdminAlreadyExists() {
        when(accountRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        new AdminAccountSeeder(accountRepository, passwordEncoder, "admin", "changeme123").run();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void skipsSeedingWhenAdminUsernameIsTakenByNonAdminAccount() {
        when(accountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(accountRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new AnalystAccount("admin", "hash", Role.ANALYST)));

        new AdminAccountSeeder(accountRepository, passwordEncoder, "admin", "changeme123").run();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void seedsAdminWhenNoneExistsAndUsernameIsFree() {
        when(accountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(accountRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("changeme123")).thenReturn("encoded-pw");

        new AdminAccountSeeder(accountRepository, passwordEncoder, "admin", "changeme123").run();

        verify(accountRepository).save(any(AnalystAccount.class));
    }
}
