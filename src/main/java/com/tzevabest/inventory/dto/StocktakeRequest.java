package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Инвентаризация: абсолютное количество и обязательная причина. */
public record StocktakeRequest(
        @NotNull Long variantId,
        @NotNull Long warehouseId,
        @NotNull @PositiveOrZero @Max(1_000_000) Integer countedQty,
        @NotBlank String reason) {
}
