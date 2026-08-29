package com.tzevabest.service.pricing;

import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;

/** Разбивка стоимости по виду работ: расчётная площадь и диапазон. */
public record WorkTypeCost(WorkType workType, BigDecimal areaSqm, BigDecimal priceMin, BigDecimal priceMax) {
}
