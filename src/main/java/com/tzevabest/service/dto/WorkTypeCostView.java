package com.tzevabest.service.dto;

import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;

/** Строка разбивки расчёта по виду работ. */
public record WorkTypeCostView(WorkType workType, BigDecimal areaSqm, BigDecimal priceMin, BigDecimal priceMax) {
}
