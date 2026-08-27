package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.PaintType;

import java.math.BigDecimal;

/** Строка списка товаров админки (лёгкая проекция с денормализованными полями и именем). */
public record ProductListItem(
        long id,
        String sku,
        long categoryId,
        long brandId,
        PaintType paintType,
        boolean active,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean hasStock,
        String name) {
}
