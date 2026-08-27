package com.tzevabest.catalog.dto;

import java.util.List;

/**
 * Категория для чтения. name/description — локализованные (с подстановкой HE при отсутствии перевода).
 * productCount — число активных товаров в категории с учётом вложенных. children заполнен только в
 * древовидном представлении (иначе null).
 */
public record CategoryResponse(
        long id,
        String slug,
        Long parentId,
        int sortOrder,
        boolean active,
        String imageUrl,
        String name,
        String description,
        long productCount,
        List<CategoryResponse> children) {
}
