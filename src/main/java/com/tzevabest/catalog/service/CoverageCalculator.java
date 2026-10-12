package com.tzevabest.catalog.service;

import com.tzevabest.catalog.dto.CoverageCalcResult;
import com.tzevabest.catalog.dto.VariantCoverage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Чистый расчёт расхода краски (B-04) — без обращения к БД, чтобы граничные случаи покрывались
 * юнит-тестами. Одна банка объёмом V литров при расходе C м²/литр красит C·V м² в один слой.
 * Нужный объём: площадь · слои / расход; банок: округление вверх (минимум одна); остаток —
 * непокрытый краской объём в купленных банках.
 */
public final class CoverageCalculator {

    private CoverageCalculator() {
    }

    /** Вариант-вход: только то, что нужно расчёту. */
    public record Variant(long id, String sku, String colorCode, String colorHex,
                          BigDecimal volumeLiters, BigDecimal price) {
    }

    public static CoverageCalcResult calculate(BigDecimal coverageSqmPerLiter, BigDecimal area, int coats,
                                               List<Variant> variants) {
        // Расход не задан или неположителен — расчёт невозможен (это не ошибка).
        if (coverageSqmPerLiter == null || coverageSqmPerLiter.signum() <= 0) {
            return CoverageCalcResult.notCalculable(area, coats);
        }

        BigDecimal neededLiters = area
                .multiply(BigDecimal.valueOf(coats))
                .divide(coverageSqmPerLiter, 4, RoundingMode.HALF_UP);

        List<VariantCoverage> rows = variants.stream()
                .filter(v -> v.volumeLiters() != null && v.volumeLiters().signum() > 0)
                .map(v -> {
                    int cans = neededLiters.divide(v.volumeLiters(), 0, RoundingMode.CEILING).intValueExact();
                    if (cans < 1) {
                        cans = 1; // хоть немного краски — уже нужна одна банка
                    }
                    BigDecimal leftover = v.volumeLiters()
                            .multiply(BigDecimal.valueOf(cans))
                            .subtract(neededLiters)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new VariantCoverage(v.id(), v.sku(), v.colorCode(), v.colorHex(),
                            v.volumeLiters(), v.price(), cans, leftover);
                })
                .toList();

        return new CoverageCalcResult(true, area, coats,
                coverageSqmPerLiter.stripTrailingZeros(), rows);
    }
}
