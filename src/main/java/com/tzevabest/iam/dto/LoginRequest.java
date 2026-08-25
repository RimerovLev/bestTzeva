package com.tzevabest.iam.dto;

import jakarta.validation.constraints.NotBlank;

/** Вход по email или телефону. */
public record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
