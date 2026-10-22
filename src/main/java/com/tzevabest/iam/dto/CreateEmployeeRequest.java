package com.tzevabest.iam.dto;

import com.tzevabest.iam.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Заведение сотрудника администратором: пароль не задаётся — приходит по ссылке-приглашению. */
public record CreateEmployeeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull UserRole role) {
}
