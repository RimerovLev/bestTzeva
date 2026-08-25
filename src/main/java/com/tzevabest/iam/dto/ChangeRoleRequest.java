package com.tzevabest.iam.dto;

import com.tzevabest.iam.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull UserRole role) {
}
