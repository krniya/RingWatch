package com.ringwatch.auth.config;

import com.ringwatch.auth.model.AnalystAccount;
import com.ringwatch.auth.model.Role;
import com.ringwatch.auth.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminAccountSeeder(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ringwatch.admin.username}") String adminUsername,
            @Value("${ringwatch.admin.password}") String adminPassword) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        AnalystAccount admin = new AnalystAccount(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN);
        accountRepository.save(admin);
        log.info("Seeded initial ADMIN account '{}' — change the password via a real flow before any real deployment.", adminUsername);
    }
}
