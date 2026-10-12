package com.tzevabest.catalog.dto;

import java.math.BigDecimal;

/**
 * Расчёт по одному варианту: сколько банок нужно и сколько краски (в литрах) останется.
 */
public record VariantCoverage(
        long variantId,
        String sku,
        String colorCode,
        String colorHex,
        BigDecimal volumeLiters,
        BigDecimal price,
        int cans,
        BigDecimal leftoverLiters) {
}
