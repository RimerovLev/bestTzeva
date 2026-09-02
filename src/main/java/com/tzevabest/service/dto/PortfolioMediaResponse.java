package com.tzevabest.service.dto;

/** Ответ на загрузку медиа проекта. */
public record PortfolioMediaResponse(long id, String url, String kind, String pairGroup, int sortOrder) {
}
