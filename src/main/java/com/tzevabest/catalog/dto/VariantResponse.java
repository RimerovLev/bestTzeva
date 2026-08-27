package com.tzevabest.catalog.dto;

import java.math.BigDecimal;

/** Вариант в ответе агрегата. */
public record VariantResponse(
        long id,
        String sku,
        String colorCode,
        String colorHex,
        BigDecimal volumeLiters,
        BigDecimal weightKg,
        BigDecimal price,
        BigDecimal oldPrice,
        String barcode,
        boolean active) {
}
