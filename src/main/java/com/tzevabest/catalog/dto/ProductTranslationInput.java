package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/** Перевод товара на одну локаль. name обязателен. */
public record ProductTranslationInput(
        @NotBlank String name,
        String shortDescription,
        String description,
        String usageInstructions,
        String metaTitle,
        String metaDescription) {
}
