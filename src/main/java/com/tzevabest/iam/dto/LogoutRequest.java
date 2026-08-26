package com.tzevabest.iam.dto;

import jakarta.validation.constraints.NotBlank;

/** refresh_token обязателен: отзывается ровно одна сессия. Массовый отзыв — отдельный /auth/logout-all. */
public record LogoutRequest(@NotBlank String refreshToken) {
}
