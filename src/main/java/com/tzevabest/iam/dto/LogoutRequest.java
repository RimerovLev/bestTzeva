package com.tzevabest.iam.dto;

/** refresh_token опционален: если задан — отзывается одна сессия, иначе все сессии пользователя. */
public record LogoutRequest(String refreshToken) {
}
