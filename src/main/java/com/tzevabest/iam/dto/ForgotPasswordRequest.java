package com.tzevabest.iam.dto;

import jakarta.validation.constraints.NotBlank;

/** Сброс пароля по email или телефону. */
public record ForgotPasswordRequest(@NotBlank String login) {
}
