package com.tzevabest.catalog.dto;

import java.math.BigDecimal;

/** Вариант в публичной карточке: цена, доступный остаток и признак «можно добавить в заявку». */
public record VariantCard(
        long id,
        String sku,
        String colorCode,
        String colorHex,
        BigDecimal volumeLiters,
        BigDecimal price,
        BigDecimal oldPrice,
        int availableQty,
        boolean canAddToRequest) {
}
