package com.ringwatch.auth.controller.dto;

import com.ringwatch.auth.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotNull Role role
) {
}
