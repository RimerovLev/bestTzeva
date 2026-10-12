package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Запрос расчёта расхода краски (B-04). Площадь в м², число слоёв — по умолчанию 2.
 */
public record CoverageCalcRequest(
        @NotNull @Positive BigDecimal area,
        @Min(1) @Max(10) Integer coats) {

    public int coatsOrDefault() {
        return coats != null ? coats : 2;
    }
}
