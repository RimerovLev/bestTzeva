package com.tzevabest.service.pricing;

import java.math.BigDecimal;

/** Тариф на вид работ: диапазон ₪/м² и минимальная стоимость заказа. */
public record PricingRule(BigDecimal pricePerSqmMin, BigDecimal pricePerSqmMax, BigDecimal minOrderPrice) {
}
