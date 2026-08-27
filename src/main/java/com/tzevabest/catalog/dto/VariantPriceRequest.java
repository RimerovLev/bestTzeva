package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Точечное изменение цены варианта. */
public record VariantPriceRequest(@NotNull @Positive BigDecimal price, @Positive BigDecimal oldPrice) {
}
