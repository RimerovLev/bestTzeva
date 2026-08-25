package com.tzevabest.common.web.security;

/** Аутентифицированный пользователь в SecurityContext (из access-токена). */
public record AuthPrincipal(long userId, String role, String email, String locale) {
}
