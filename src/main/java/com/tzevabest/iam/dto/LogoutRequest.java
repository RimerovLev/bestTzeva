package com.tzevabest.iam.dto;

/**
 * Тело запроса выхода. refresh_token — необязательный запасной путь для клиентов без кук; при
 * работе по куке тело отсутствует. Отзывается ровно одна сессия. Массовый отзыв — /auth/logout-all.
 */
public record LogoutRequest(String refreshToken) {
}
