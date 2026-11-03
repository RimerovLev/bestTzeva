package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Создание бренда из админки: достаточно названия, slug генерится автоматически. */
public record BrandCreate(@NotBlank @Size(max = 255) String name) {
}
