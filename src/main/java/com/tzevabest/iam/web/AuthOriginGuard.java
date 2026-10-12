package com.tzevabest.iam.web;

import com.tzevabest.common.web.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Защита refresh/logout от CSRF по заголовку Origin (B-01.5).
 * <p>
 * {@code SameSite=Lax} снимает CSRF только при разных доменах фронта и API. Если админка и API
 * окажутся на одном домене, Lax перестаёт защищать — тогда единственный барьер это проверка
 * Origin против того же allow-list, что и CORS.
 * <p>
 * Проверяется только когда Origin присутствует (браузерный кросс-контекстный POST его шлёт всегда).
 * Его отсутствие допустимо: не-браузерные клиенты (мобильное приложение) ходят по пути с токеном в
 * теле и Origin не ставят — им кука всё равно не достаётся.
 */
@Component
public class AuthOriginGuard {

    private final Set<String> allowedOrigins;

    public AuthOriginGuard(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    /** Бросает 403, если Origin запроса задан и не входит в allow-list. */
    public void check(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new BusinessException("common.auth.origin_not_allowed", HttpStatus.FORBIDDEN);
        }
    }
}
