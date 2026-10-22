package com.tzevabest.iam.dto;

import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;

/** Строка списка сотрудников для админки (управление персоналом). */
public record EmployeeView(
        long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status) {
}
