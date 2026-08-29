package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.pricing.PricingOption;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

/** Запрос предварительного расчёта стоимости покраски. */
public record CalculateRequest(
        @NotNull PropertyType propertyType,
        @NotNull @DecimalMin("1.0") @DecimalMax("100000.0") BigDecimal areaSqm,
        @DecimalMin("1.5") @DecimalMax("20.0") BigDecimal ceilingHeightM,
        @Min(0) @Max(1000) Integer roomsCount,
        @NotEmpty @Size(max = 6) Set<WorkType> workTypes,
        Set<PricingOption> options,
        boolean urgent) {
}
