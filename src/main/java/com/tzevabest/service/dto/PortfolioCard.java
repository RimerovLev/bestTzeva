package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Карточка проекта в публичном списке портфолио. */
public record PortfolioCard(
        long id, String slug, String title, PropertyType propertyType, String city,
        BigDecimal areaSqm, Integer durationDays, LocalDate completedAt, String mainAfterUrl) {
}
