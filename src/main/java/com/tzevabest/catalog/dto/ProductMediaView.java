package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.MediaType;

import java.util.Map;

/**
 * Медиа товара/варианта для витрины и админ-ответа: публичная ссылка, тип, порядок, флаг главного,
 * локализованные alt-тексты.
 */
public record ProductMediaView(
        long id,
        String url,
        MediaType type,
        int sortOrder,
        boolean primary,
        Map<String, String> alt) {
}
