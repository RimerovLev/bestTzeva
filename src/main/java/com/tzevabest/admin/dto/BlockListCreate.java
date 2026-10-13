package com.tzevabest.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Добавление в блок-лист. */
public record BlockListCreate(
        @NotNull @Pattern(regexp = "IP|PHONE") String kind,
        @NotBlank @Size(max = 255) String value,
        @Size(max = 255) String reason) {
}
