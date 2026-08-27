package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.dto.ProductSearchResult;
import com.tzevabest.catalog.service.ProductCatalogService;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Публичная витрина товаров. Локаль — из ?locale или Accept-Language, дефолт HE. */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService catalog;

    @GetMapping
    public ProductSearchResult search(
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "brand_id", required = false) Long brandId,
            @RequestParam(name = "paint_type", required = false) PaintType paintType,
            @RequestParam(name = "usage_area", required = false) UsageArea usageArea,
            @RequestParam(required = false) Finish finish,
            @RequestParam(name = "base_type", required = false) BaseType baseType,
            @RequestParam(name = "price_min", required = false) BigDecimal priceMin,
            @RequestParam(name = "price_max", required = false) BigDecimal priceMax,
            @RequestParam(name = "in_stock", required = false) Boolean inStock,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String locale,
            @ApiPage(sort = {"created_at", "min_price", "max_price", "sku"}) PageQuery page) {
        return catalog.search(categoryId, brandId,
                paintType != null ? paintType.name() : null,
                usageArea != null ? usageArea.name() : null,
                finish != null ? finish.name() : null,
                baseType != null ? baseType.name() : null,
                priceMin, priceMax, inStock, query,
                locale != null ? locale : LocaleContextHolder.getLocale().getLanguage(), page);
    }

    @GetMapping("/{id}")
    public ProductCard card(@PathVariable long id, @RequestParam(required = false) String locale) {
        return catalog.card(id, locale != null ? locale : LocaleContextHolder.getLocale().getLanguage());
    }
}
