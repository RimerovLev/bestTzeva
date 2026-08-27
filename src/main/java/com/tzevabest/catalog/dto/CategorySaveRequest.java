package com.tzevabest.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Создание/обновление категории. slug опционален: при отсутствии генерируется из английского
 * (или ивритского) названия; при явном значении используется как основа (с числовым суффиксом при
 * коллизии). sortOrder/active опциональны (дефолт 0 / true).
 */
public record CategorySaveRequest(
        String slug,
        Long parentId,
        Integer sortOrder,
        Boolean active,
        String imageUrl,
        @NotNull @Valid CategoryTranslationsRequest translations) {
}
