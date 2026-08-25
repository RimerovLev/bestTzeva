package com.tzevabest.common.web;

import com.tzevabest.common.dto.SortRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Разобранные и нормализованные параметры пагинации/сортировки.
 *
 * @param page номер страницы (с нуля, &ge; 0)
 * @param size размер страницы (1..{@value ApiPageArgumentResolver#MAX_SIZE})
 * @param sort сортировка
 */
public record PageQuery(int page, int size, SortRequest sort) {

    public Pageable toPageable() {
        return PageRequest.of(page, size, sort.toSort());
    }
}
