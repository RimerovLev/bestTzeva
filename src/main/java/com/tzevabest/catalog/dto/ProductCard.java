package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;

import java.math.BigDecimal;
import java.util.List;

/**
 * Публичная карточка товара (витрина). Без рейтинга и отзывов (сняты со сносом e-commerce).
 * Тексты локализованы с подстановкой HE. Показываются только активные варианты.
 */
public record ProductCard(
        long id,
        String sku,
        long categoryId,
        long brandId,
        PaintType paintType,
        UsageArea usageArea,
        Finish finish,
        BaseType baseType,
        BigDecimal coverageSqmPerLiter,
        BigDecimal dryingTimeHours,
        String name,
        String shortDescription,
        String description,
        String usageInstructions,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean hasStock,
        List<ProductMediaView> media,
        List<VariantCard> variants) {
}
