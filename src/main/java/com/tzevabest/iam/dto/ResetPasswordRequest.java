package com.tzevabest.iam.dto;

import com.tzevabest.common.web.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String token, @ValidPassword String newPassword) {
}
