package com.tzevabest.catalog.web;

import com.tzevabest.catalog.dto.CategoryResponse;
import com.tzevabest.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Публичный каталог категорий. Отдаются только активные, с числом активных товаров (с учётом
 * вложенных). Локаль — из параметра {@code locale} или заголовка Accept-Language, дефолт HE.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categories;

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean tree,
                                       @RequestParam(required = false) String locale) {
        return categories.list(tree, locale != null ? locale : currentLanguage());
    }

    @GetMapping("/{slug}")
    public CategoryResponse getBySlug(@PathVariable String slug,
                                      @RequestParam(required = false) String locale) {
        return categories.getBySlug(slug, locale != null ? locale : currentLanguage());
    }

    private static String currentLanguage() {
        return LocaleContextHolder.getLocale().getLanguage();
    }
}
