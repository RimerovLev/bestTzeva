package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/** Перевод категории на одну локаль. name обязателен, остальное опционально. */
public record CategoryTranslationInput(
        @NotBlank String name,
        String description,
        String metaTitle,
        String metaDescription) {
}
