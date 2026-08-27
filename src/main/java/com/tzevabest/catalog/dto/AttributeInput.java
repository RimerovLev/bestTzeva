package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/** Характеристика товара (EAV): код определения + значение. */
public record AttributeInput(@NotBlank String code, @NotBlank String value) {
}
