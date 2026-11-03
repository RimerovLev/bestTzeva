package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Полные данные проекта портфолио для редактирования в админке: все переводы и все медиа. */
public record PortfolioAdminView(
        long id, String slug, PropertyType propertyType, String city, BigDecimal areaSqm,
        Integer durationDays, LocalDate completedAt, Set<WorkType> workTypes, boolean published,
        List<PortfolioTranslationInput> translations, List<PortfolioMediaResponse> media) {
}
