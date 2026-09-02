package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Детали проекта портфолио: пары «до/после» готовыми парами + одиночные медиа. */
public record PortfolioProjectView(
        long id, String slug, String title, String description, String metaTitle, String metaDescription,
        PropertyType propertyType, String city, BigDecimal areaSqm, Integer durationDays, LocalDate completedAt,
        Set<WorkType> workTypes, List<MediaPair> pairs, List<MediaItem> standalone) {
}
