package com.ringwatch.auth.controller;

import com.ringwatch.auth.controller.dto.AccountResponse;
import com.ringwatch.auth.controller.dto.CreateAccountRequest;
import com.ringwatch.auth.controller.dto.LoginRequest;
import com.ringwatch.auth.controller.dto.LoginResponse;
import com.ringwatch.auth.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/auth/me")
    public AccountResponse me(Authentication authentication) {
        UUID accountId = UUID.fromString(authentication.getName());
        return AccountResponse.from(authService.getById(accountId));
    }

    @PostMapping("/auth/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(authService.createAccount(request));
    }
}
