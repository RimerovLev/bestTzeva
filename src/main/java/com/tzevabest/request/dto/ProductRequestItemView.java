package com.tzevabest.request.dto;

import java.math.BigDecimal;

/** Позиция в ответе (снимки на момент подачи). */
public record ProductRequestItemView(
        long variantId,
        int qty,
        String productName,
        String sku,
        String color,
        BigDecimal volumeLiters,
        BigDecimal price) {
}
