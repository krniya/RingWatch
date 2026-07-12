package com.ringwatch.auth.service;

import com.ringwatch.auth.controller.dto.CreateAccountRequest;
import com.ringwatch.auth.controller.dto.LoginRequest;
import com.ringwatch.auth.controller.dto.LoginResponse;
import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.repository.AccountRepository;
import com.ringwatch.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        AnalystAccount account = accountRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(account);
        return new LoginResponse(token, jwtService.getExpirationSeconds());
    }

    public AnalystAccount createAccount(CreateAccountRequest request) {
        if (accountRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameTakenException(request.username());
        }

        AnalystAccount account = new AnalystAccount(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role());

        return accountRepository.save(account);
    }

    public AnalystAccount getById(java.util.UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }
}
