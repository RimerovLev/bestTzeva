package com.tzevabest.iam.web;

import com.tzevabest.iam.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh-токен переносится в httpOnly-куке (B-01), а не в теле ответа: любой XSS в админке
 * иначе отдал бы злоумышленнику 30-дневную сессию сотрудника. Кука недоступна из JS.
 *
 * <ul>
 *   <li>{@code HttpOnly} — не читается скриптом;</li>
 *   <li>{@code Secure} — только по HTTPS (выключается в local/test, где http);</li>
 *   <li>{@code SameSite=Lax} — браузер не шлёт куку на кросс-сайтовые POST (базовая защита от CSRF;
 *       на один домен с API дополняется проверкой Origin, см. {@link AuthOriginGuard});</li>
 *   <li>{@code Path=/api/v1/auth} — кука уходит только на эндпоинты аутентификации, не на весь API;</li>
 *   <li>{@code Max-Age} = сроку жизни refresh.</li>
 * </ul>
 */
@Component
public class RefreshCookieService {

    private final JwtService jwt;
    private final boolean enabled;
    private final String name;
    private final String path;
    private final String sameSite;
    private final boolean secure;

    public RefreshCookieService(
            JwtService jwt,
            @Value("${app.auth.refresh-cookie.enabled:true}") boolean enabled,
            @Value("${app.auth.refresh-cookie.name:refresh_token}") String name,
            @Value("${app.auth.refresh-cookie.path:/api/v1/auth}") String path,
            @Value("${app.auth.refresh-cookie.same-site:Lax}") String sameSite,
            @Value("${app.auth.refresh-cookie.secure:true}") boolean secure) {
        this.jwt = jwt;
        this.enabled = enabled;
        this.name = name;
        this.path = path;
        this.sameSite = sameSite;
        this.secure = secure;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Кука с выданным refresh-токеном на весь срок его жизни. */
    public ResponseCookie issue(String token) {
        return base(token)
                .maxAge(jwt.refreshTtl())
                .build();
    }

    /** Гашение куки: пустое значение и Max-Age=0 (logout / logout-all). */
    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    /** Значение refresh-куки из запроса, либо {@code null}, если её нет. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value != null && !value.isBlank()) ? value : null;
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}
