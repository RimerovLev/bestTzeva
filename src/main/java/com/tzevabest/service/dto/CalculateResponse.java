package com.tzevabest.service.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ответ расчёта: диапазон, разбивка, флаг оценки и оговорка на локали запроса. */
public record CalculateResponse(
        BigDecimal priceMin,
        BigDecimal priceMax,
        List<WorkTypeCostView> breakdown,
        boolean isEstimate,
        String disclaimer) {
}
