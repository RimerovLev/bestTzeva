package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Списание с обязательной причиной. */
public record WriteOffRequest(
        @NotNull Long variantId,
        @NotNull Long warehouseId,
        @NotNull @Positive @Max(1_000_000) Integer qty,
        @NotBlank String reason) {
}
