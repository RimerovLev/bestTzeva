package com.tzevabest.catalog.dto;

import com.tzevabest.common.dto.PageResponse;

/** Результат публичного поиска каталога: страница товаров + фасеты по всей выборке. */
public record ProductSearchResult(PageResponse<ProductSummary> products, ProductFacets facets) {
}
