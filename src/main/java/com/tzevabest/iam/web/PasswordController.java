package com.tzevabest.iam.web;

import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.iam.dto.ChangePasswordRequest;
import com.tzevabest.iam.dto.ForgotPasswordRequest;
import com.tzevabest.iam.dto.ResetPasswordRequest;
import com.tzevabest.iam.service.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    /** Ответ одинаков независимо от существования аккаунта. */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimit(key = "field:login", limit = 3, windowSeconds = 3600)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgot(request.login());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.reset(request.token(), request.newPassword());
    }

    @PostMapping("/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Текущий пароль проверяется подбором — ограничиваем частоту по пользователю (аудит п.16).
    @RateLimit(key = "user", limit = 5, windowSeconds = 900)
    public void change(@AuthenticationPrincipal AuthPrincipal principal,
                       @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.change(principal.userId(), request.currentPassword(), request.newPassword());
    }
}
