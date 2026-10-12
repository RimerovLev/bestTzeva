package com.tzevabest.iam.web;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.ratelimit.CountOn;
import com.tzevabest.common.web.ratelimit.RateLimit;
import org.springframework.beans.factory.annotation.Value;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.LogoutRequest;
import com.tzevabest.iam.dto.MeResponse;
import com.tzevabest.iam.dto.RefreshRequest;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.dto.TokenResponse;
import com.tzevabest.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Аутентификация: /api/v1/auth/** (префикс /api/v1 добавляется маппингом).
 * <p>
 * Refresh-токен переносится в httpOnly-куке (B-01): login/register/refresh ставят её, refresh и
 * logout читают токен из неё. В теле ответа остаются только {@code access_token}, {@code token_type}
 * и {@code expires_in}. Приём и выдача токена в теле — запасной путь для клиентов без кук
 * (мобильное приложение) за флагом {@code app.auth.refresh-token-in-body}.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookie;
    private final AuthOriginGuard originGuard;

    @Value("${app.auth.public-registration-enabled:false}")
    private boolean publicRegistrationEnabled;

    /** Запасной путь: принимать refresh из тела и возвращать его в теле (клиенты без кук). */
    @Value("${app.auth.refresh-token-in-body:false}")
    private boolean refreshTokenInBody;

    @PostMapping("/register")
    @RateLimit(key = "ip", limit = 5, windowSeconds = 3600)
    @Operation(summary = "Регистрация", description = "Ставит refresh в httpOnly-куку; в теле — только access.")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest http) {
        if (!publicRegistrationEnabled) {
            // Регистрация закрыта: отвечаем 404, не подтверждая существование закрытого эндпоинта.
            throw new NotFoundException("common.resource.not_found");
        }
        TokenResponse tokens = authService.register(request, userAgent(http), ip(http));
        return issued(HttpStatus.CREATED, tokens);
    }

    @PostMapping("/login")
    @RateLimit(key = "ip", limit = 10, windowSeconds = 300)
    @RateLimit(key = "field:login", limit = 5, windowSeconds = 900, countOn = CountOn.FAILURE)
    @Operation(summary = "Вход по email или телефону", description = "Ставит refresh в httpOnly-куку; в теле — только access.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        TokenResponse tokens = authService.login(request, userAgent(http), ip(http));
        return issued(HttpStatus.OK, tokens);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление токена",
            description = "Читает refresh из httpOnly-куки (или из тела за флагом). Проверяет Origin.")
    public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                 HttpServletRequest http) {
        originGuard.check(http);
        String token = resolveRefreshToken(request, http);
        if (token == null) {
            // Ни куки, ни тела — считаем refresh недействительным (как истёкшую сессию).
            throw new BusinessException("common.auth.refresh_invalid", HttpStatus.UNAUTHORIZED);
        }
        TokenResponse tokens = authService.refresh(token, userAgent(http), ip(http));
        return issued(HttpStatus.OK, tokens);
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из одной сессии", description = "Отзывает предъявленный refresh и гасит куку.")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal,
                                       HttpServletRequest http) {
        originGuard.check(http);
        String token = resolveLogoutToken(request, http);
        if (token == null) {
            // Пустой logout не гасит все сессии — это ошибка запроса (см. logout-all для массового отзыва).
            throw new BusinessException("common.auth.refresh_required", HttpStatus.BAD_REQUEST);
        }
        authService.logout(principal.userId(), token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie())
                .build();
    }

    /** Массовый отзыв: гасит все сессии текущего пользователя (явное действие, не дефолт logout). */
    @PostMapping("/logout-all")
    @Operation(summary = "Выход на всех устройствах", description = "Гасит все сессии пользователя и куку.")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal,
                                          HttpServletRequest http) {
        originGuard.check(http);
        authService.logoutAll(principal.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.userId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** Ответ с выданными токенами: refresh — в куку, тело без refresh (если не включён body-путь). */
    private ResponseEntity<TokenResponse> issued(HttpStatus status, TokenResponse tokens) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (refreshCookie.enabled()) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie.issue(tokens.refreshToken()).toString());
        }
        return builder.body(bodyOf(tokens));
    }

    /** Тело ответа: access всегда, refresh — только когда включён запасной body-путь (иначе null → опущен). */
    private TokenResponse bodyOf(TokenResponse tokens) {
        if (refreshTokenInBody) {
            return tokens;
        }
        return new TokenResponse(tokens.accessToken(), null, tokens.tokenType(), tokens.expiresIn());
    }

    private String resolveRefreshToken(RefreshRequest request, HttpServletRequest http) {
        String fromCookie = refreshCookie.read(http);
        if (fromCookie != null) {
            return fromCookie;
        }
        return fromBody(request != null ? request.refreshToken() : null);
    }

    private String resolveLogoutToken(LogoutRequest request, HttpServletRequest http) {
        String fromCookie = refreshCookie.read(http);
        if (fromCookie != null) {
            return fromCookie;
        }
        return fromBody(request != null ? request.refreshToken() : null);
    }

    /** Токен из тела — только если включён запасной body-путь; пустая строка → null. */
    private String fromBody(String bodyToken) {
        if (!refreshTokenInBody || bodyToken == null || bodyToken.isBlank()) {
            return null;
        }
        return bodyToken;
    }

    private String clearCookie() {
        ResponseCookie cookie = refreshCookie.clear();
        return cookie.toString();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
