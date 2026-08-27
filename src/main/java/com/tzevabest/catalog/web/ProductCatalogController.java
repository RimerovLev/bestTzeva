package com.tzevabest.catalog.web;

import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Публичная витрина товаров. Локаль — из ?locale или Accept-Language, дефолт HE. */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService catalog;

    @GetMapping("/{id}")
    public ProductCard card(@PathVariable long id, @RequestParam(required = false) String locale) {
        return catalog.card(id, locale != null ? locale : LocaleContextHolder.getLocale().getLanguage());
    }
}
