package com.tzevabest.service.dto;

import java.math.BigDecimal;

/** Тариф для админ-выдачи/редактирования. */
public record PricingRuleView(
        long id, String propertyType, String workType,
        BigDecimal pricePerSqmMin, BigDecimal pricePerSqmMax, BigDecimal minOrderPrice, boolean active) {
}
