package com.tzevabest.service.pricing;

import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.util.Map;

/** Тарифы по видам работ и множители-коэффициенты (по коду), уже выбранные для типа объекта. */
public record Ruleset(Map<WorkType, PricingRule> rules, Map<String, BigDecimal> coefficients) {
}
