package com.tzevabest.service.pricing;

import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.util.Set;

/** Вход калькулятора: параметры объекта, виды работ, опции, срочность. */
public record PricingInput(
        BigDecimal areaSqm,
        BigDecimal ceilingHeightM,
        Set<WorkType> workTypes,
        Set<PricingOption> options,
        boolean urgent) {
}
