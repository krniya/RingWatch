package com.ringwatch.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ringwatch.auth.controller.dto.CreateAccountRequest;
import com.ringwatch.auth.controller.dto.LoginRequest;
import com.ringwatch.auth.controller.dto.LoginResponse;
import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import com.ringwatch.auth.repository.AccountRepository;
import com.ringwatch.auth.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(accountRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        AnalystAccount account = new AnalystAccount("jdoe", "hashed-pw", Role.ANALYST);
        account.setId(UUID.randomUUID());
        when(accountRepository.findByUsername("jdoe")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);
        when(jwtService.generateToken(account)).thenReturn("signed-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("jdoe", "secret"));

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void loginFailsForUnknownUsername() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "secret")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsForWrongPassword() {
        AnalystAccount account = new AnalystAccount("jdoe", "hashed-pw", Role.ANALYST);
        account.setId(UUID.randomUUID());
        when(accountRepository.findByUsername("jdoe")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jdoe", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void createAccountRejectsDuplicateUsername() {
        when(accountRepository.findByUsername("jdoe"))
                .thenReturn(Optional.of(new AnalystAccount("jdoe", "hash", Role.ANALYST)));

        assertThatThrownBy(() -> authService.createAccount(
                new CreateAccountRequest("jdoe", "password123", Role.ANALYST)))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void createAccountEncodesPasswordAndSaves() {
        when(accountRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pw");
        when(accountRepository.save(any(AnalystAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AnalystAccount created = authService.createAccount(
                new CreateAccountRequest("newuser", "password123", Role.ANALYST));

        assertThat(created.getUsername()).isEqualTo("newuser");
        assertThat(created.getPasswordHash()).isEqualTo("encoded-pw");
        assertThat(created.getRole()).isEqualTo(Role.ANALYST);
    }
}
