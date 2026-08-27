package com.tzevabest.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Переводы категории в одном запросе. HE обязателен, RU и EN опциональны. */
public record CategoryTranslationsRequest(
        @NotNull @Valid CategoryTranslationInput he,
        @Valid CategoryTranslationInput ru,
        @Valid CategoryTranslationInput en) {
}
