package com.tzevabest.iam.web;

import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.LogoutRequest;
import com.tzevabest.iam.dto.MeResponse;
import com.tzevabest.iam.dto.RefreshRequest;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.dto.TokenResponse;
import com.tzevabest.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Аутентификация: /api/v1/auth/** (префикс /api/v1 добавляется маппингом). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(key = "ip", limit = 5, windowSeconds = 3600)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, userAgent(http), ip(http));
    }

    @PostMapping("/login")
    @RateLimit(key = "ip", limit = 10, windowSeconds = 300)
    @RateLimit(key = "field:login", limit = 5, windowSeconds = 900)
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, userAgent(http), ip(http));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), userAgent(http), ip(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal.userId(), request.refreshToken());
    }

    /** Массовый отзыв: гасит все сессии текущего пользователя (явное действие, не дефолт logout). */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logoutAll(principal.userId());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.userId());
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
