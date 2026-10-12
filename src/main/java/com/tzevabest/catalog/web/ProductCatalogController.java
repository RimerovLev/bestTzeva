package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.catalog.dto.CoverageCalcRequest;
import com.tzevabest.catalog.dto.CoverageCalcResult;
import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.dto.ProductSearchResult;
import com.tzevabest.catalog.service.ProductCatalogService;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Карточка товара. Основной публичный адрес — по slug (индексируемый, устаревший даёт 301, B-02);
     * чисто числовой ключ трактуется как id и оставлен для админки/внутренних вызовов. Один маршрут
     * вместо двух — чтобы не полагаться на разрешение неоднозначности {id} против {slug}.
     */
    @GetMapping("/{key}")
    public ProductCard card(@PathVariable String key, @RequestParam(required = false) String locale) {
        String resolved = locale != null ? locale : LocaleContextHolder.getLocale().getLanguage();
        return key.matches("\\d+")
                ? catalog.card(Long.parseLong(key), resolved)
                : catalog.cardBySlug(key, resolved);
    }

    /** Расчёт расхода краски по товару (B-04): площадь + число слоёв → банки и остаток по вариантам. */
    @PostMapping("/{slug}/coverage-calc")
    public CoverageCalcResult coverageCalc(@PathVariable String slug,
                                           @Valid @RequestBody CoverageCalcRequest request) {
        return catalog.coverageCalc(slug, request.area(), request.coatsOrDefault());
    }
}
