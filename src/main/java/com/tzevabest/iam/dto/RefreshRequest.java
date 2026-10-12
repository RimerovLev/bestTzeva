package com.tzevabest.iam.dto;

/**
 * Тело запроса обновления токена. refresh_token — необязательный запасной путь для клиентов
 * без кук (мобильное приложение, за флагом app.auth.refresh-token-in-body). Основной путь —
 * httpOnly-кука, тогда тело отсутствует вовсе (B-01).
 */
public record RefreshRequest(String refreshToken) {
}
