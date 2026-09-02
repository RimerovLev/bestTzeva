package com.tzevabest.service.dto;

/** Одиночное медиа проекта (без пары). */
public record MediaItem(String url, String kind, int sortOrder) {
}
