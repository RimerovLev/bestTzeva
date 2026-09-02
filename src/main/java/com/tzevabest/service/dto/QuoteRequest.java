package com.tzevabest.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Выставление котировки: цена, состав работ, срок, комментарий. Генерирует PDF и уведомляет клиента. */
public record QuoteRequest(
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Size(max = 2000) String workComposition,
        @Positive @Max(3650) Integer termDays,
        @Size(max = 2000) String comment) {
}
