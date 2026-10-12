package com.tzevabest.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Результат расчёта расхода. {@code calculable=false}, если у товара не задан расход
 * (coverage) — тогда это не ошибка, а признак невозможности расчёта (B-04).
 */
public record CoverageCalcResult(
        boolean calculable,
        BigDecimal area,
        int coats,
        BigDecimal coverageSqmPerLiter,
        List<VariantCoverage> variants) {

    public static CoverageCalcResult notCalculable(BigDecimal area, int coats) {
        return new CoverageCalcResult(false, area, coats, null, List.of());
    }
}
