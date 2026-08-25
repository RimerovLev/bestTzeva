package com.tzevabest.iam.dto;

import com.tzevabest.iam.domain.UserRole;

public record MeResponse(long id, String email, String phone, String firstName, String lastName,
                         UserRole role, String locale) {
}
