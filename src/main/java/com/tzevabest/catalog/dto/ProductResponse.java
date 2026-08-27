package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Товар-агрегат для админки. Денормализованные min_price/max_price/has_stock отражают текущее
 * состояние вариантов и остатков. translations — по локалям, attributes — код→значение.
 */
public record ProductResponse(
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
        boolean active,
        boolean featured,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean hasStock,
        Map<String, ProductTranslationInput> translations,
        Map<String, String> attributes,
        List<VariantResponse> variants) {
}
