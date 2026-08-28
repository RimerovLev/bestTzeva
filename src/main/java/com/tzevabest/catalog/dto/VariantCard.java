package com.tzevabest.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Вариант в публичной карточке. Точный остаток наружу не раскрывается (коммерческая информация,
 * R-47) — только текстовая градация availability: {@code many} / {@code few} / {@code out_of_stock}.
 */
public record VariantCard(
        long id,
        String sku,
        String colorCode,
        String colorHex,
        BigDecimal volumeLiters,
        BigDecimal price,
        BigDecimal oldPrice,
        String availability,
        boolean canAddToRequest,
        List<ProductMediaView> media) {
}
