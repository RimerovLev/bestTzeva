package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Позиция массового прихода: SKU варианта и количество. */
public record ReceiptLine(
        @NotBlank String sku,
        @NotNull @Positive @Max(1_000_000) Integer qty) {
}
