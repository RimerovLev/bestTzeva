package com.tzevabest.iam.dto;

import com.tzevabest.common.web.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Регистрация: email ИЛИ телефон (проверяется в сервисе) + пароль + имя. */
public record RegisterRequest(
        @Email String email,
        String phone,
        @ValidPassword String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String locale) {
}
