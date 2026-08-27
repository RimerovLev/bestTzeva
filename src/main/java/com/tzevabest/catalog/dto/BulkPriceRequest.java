package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.PaintType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Массовое изменение цен вариантов по фильтру (все опциональны — их пересечение). percent —
 * относительное изменение в процентах (5 → +5%, -10 → -10%); должен быть > -100.
 */
public record BulkPriceRequest(
        Long brandId,
        Long categoryId,
        PaintType paintType,
        @NotNull BigDecimal percent) {
}
