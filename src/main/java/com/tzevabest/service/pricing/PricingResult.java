package com.tzevabest.service.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Результат: диапазон, разбивка, флаг оценки (не окончательная цена). */
public record PricingResult(BigDecimal priceMin, BigDecimal priceMax, List<WorkTypeCost> breakdown, boolean estimate) {
}
