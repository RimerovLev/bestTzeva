package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;

import java.time.LocalDate;

/** Строка списка портфолио в админке: включая черновики и флаг публикации. */
public record PortfolioAdminCard(
        long id, String slug, String title, PropertyType propertyType, String city,
        LocalDate completedAt, boolean published, int mediaCount) {
}
