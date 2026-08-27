package com.tzevabest.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Переводы товара в одном запросе. HE обязателен, RU/EN опциональны. */
public record ProductTranslationsRequest(
        @NotNull @Valid ProductTranslationInput he,
        @Valid ProductTranslationInput ru,
        @Valid ProductTranslationInput en) {
}
