package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.dto.BulkPriceRequest;
import com.tzevabest.catalog.dto.ProductActiveRequest;
import com.tzevabest.catalog.dto.ProductListItem;
import com.tzevabest.catalog.dto.ProductResponse;
import com.tzevabest.catalog.dto.ProductSaveRequest;
import com.tzevabest.catalog.dto.VariantPriceRequest;
import com.tzevabest.catalog.dto.VariantResponse;
import com.tzevabest.catalog.service.ProductService;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Админ-управление товарами и вариантами. Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminProductController {

    private final ProductService products;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductSaveRequest request) {
        return products.create(request);
    }

    /** Массовое изменение цен по фильтру. До {@code /{id}}, чтобы не толковаться как id. */
    @PostMapping("/bulk-price")
    public Map<String, Object> bulkPrice(@Valid @RequestBody BulkPriceRequest request) {
        return Map.of("affected_variants", products.bulkChangePrices(request));
    }

    @GetMapping
    public PageResponse<ProductListItem> list(
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "brand_id", required = false) Long brandId,
            @RequestParam(name = "paint_type", required = false) PaintType paintType,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "in_stock", required = false) Boolean inStock,
            @RequestParam(name = "price_min", required = false) BigDecimal priceMin,
            @RequestParam(name = "price_max", required = false) BigDecimal priceMax,
            @RequestParam(name = "q", required = false) String query,
            @ApiPage(sort = {"created_at", "sku", "min_price", "max_price"}) PageQuery page) {
        return products.list(categoryId, brandId, paintType, active, inStock, priceMin, priceMax, query, page);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductSaveRequest request) {
        return products.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public ProductResponse changeActive(@PathVariable long id, @Valid @RequestBody ProductActiveRequest request) {
        return products.changeActive(id, request.active());
    }

    @PatchMapping("/variants/{variantId}/price")
    public VariantResponse changeVariantPrice(@PathVariable long variantId,
                                              @Valid @RequestBody VariantPriceRequest request) {
        return products.changeVariantPrice(variantId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @RequestParam(defaultValue = "false") boolean hard) {
        products.delete(id, hard);
    }
}
