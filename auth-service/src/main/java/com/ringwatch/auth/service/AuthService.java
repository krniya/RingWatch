package com.ringwatch.auth.service;

import com.ringwatch.auth.controller.dto.CreateAccountRequest;
import com.ringwatch.auth.controller.dto.LoginRequest;
import com.ringwatch.auth.controller.dto.LoginResponse;
import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.repository.AccountRepository;
import com.ringwatch.auth.security.JwtService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

        try {
            return accountRepository.save(account);
        } catch (DataIntegrityViolationException e) {
            throw new UsernameTakenException(request.username());
        }
    }

    public AnalystAccount getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }
}
