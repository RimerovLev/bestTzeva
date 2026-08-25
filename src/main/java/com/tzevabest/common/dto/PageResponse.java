package com.tzevabest.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Единая обёртка страницы для всех списочных эндпоинтов — вместо «голого» Spring {@link Page}.
 * Имена полей сериализуются в snake_case: {@code total_elements}, {@code total_pages}.
 *
 * @param items        элементы текущей страницы
 * @param page         номер страницы (с нуля)
 * @param size         размер страницы
 * @param totalElements всего элементов
 * @param totalPages   всего страниц
 */
public record PageResponse<T>(
        @Schema(description = "Элементы текущей страницы") List<T> items,
        @Schema(description = "Номер страницы (с нуля)", example = "0") int page,
        @Schema(description = "Размер страницы", example = "20") int size,
        @Schema(description = "Всего элементов", example = "137") long totalElements,
        @Schema(description = "Всего страниц", example = "7") int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = (size > 0) ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    /** Построить из Spring {@link Page} (контент маппится вызывающим кодом заранее). */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
