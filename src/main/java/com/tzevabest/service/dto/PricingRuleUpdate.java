package com.tzevabest.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Изменение тарифа. */
public record PricingRuleUpdate(
        @NotNull @DecimalMin("0.0") BigDecimal pricePerSqmMin,
        @NotNull @DecimalMin("0.0") BigDecimal pricePerSqmMax,
        @NotNull @DecimalMin("0.0") BigDecimal minOrderPrice,
        Boolean active) {
}
