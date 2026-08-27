package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Вариант в составе агрегата. id != null — существующий (обновляется), id == null — новый.
 * hex — #RRGGBB или #RRGGBBAA. old_price (если задан) сверяется с price в сервисе.
 */
public record VariantInput(
        Long id,
        @NotBlank String sku,
        @NotBlank String colorCode,
        @Pattern(regexp = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$", message = "must be a hex color like #RRGGBB")
        String colorHex,
        @NotNull @Positive BigDecimal volumeLiters,
        @PositiveOrZero BigDecimal weightKg,
        @NotNull @Positive BigDecimal price,
        @Positive BigDecimal oldPrice,
        String barcode,
        Boolean active) {
}
