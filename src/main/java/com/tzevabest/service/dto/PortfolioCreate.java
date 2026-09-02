package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Создание/обновление проекта портфолио. Переводы с обязательным HE. */
public record PortfolioCreate(
        @NotBlank @Size(max = 140) String slug,
        PropertyType propertyType,
        @Size(max = 120) String city,
        BigDecimal areaSqm,
        Integer durationDays,
        LocalDate completedAt,
        @Size(max = 6) Set<WorkType> workTypes,
        @NotEmpty @Valid List<PortfolioTranslationInput> translations) {
}
