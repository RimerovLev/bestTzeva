package com.tzevabest.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Перевод проекта портфолио. */
public record PortfolioTranslationInput(
        @Pattern(regexp = "he|ru|en") String locale,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @Size(max = 255) String metaTitle,
        @Size(max = 500) String metaDescription) {
}
