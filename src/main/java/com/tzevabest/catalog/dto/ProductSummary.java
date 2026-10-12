package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.PaintType;

import java.math.BigDecimal;

/** Строка публичного листинга каталога (лёгкая). */
public record ProductSummary(
        long id,
        String slug,
        String sku,
        long categoryId,
        long brandId,
        PaintType paintType,
        String name,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean hasStock) {
}
