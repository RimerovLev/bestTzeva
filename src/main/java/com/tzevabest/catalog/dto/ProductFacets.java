package com.tzevabest.catalog.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Фасеты по текущей выборке: количества по типу краски и бренду, диапазон цены. */
public record ProductFacets(
        Map<String, Long> paintType,
        Map<Long, Long> brand,
        BigDecimal priceMin,
        BigDecimal priceMax) {
}
