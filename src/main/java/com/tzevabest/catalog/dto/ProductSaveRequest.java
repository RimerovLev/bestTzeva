package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Агрегат товара целиком: поля товара + переводы + характеристики + список вариантов. */
public record ProductSaveRequest(
        @NotNull Long categoryId,
        @NotNull Long brandId,
        @NotBlank String sku,
        /** Необязателен: на создании генерируется из английского названия, при смене даёт 301 (B-02). */
        String slug,
        @NotNull PaintType paintType,
        @NotNull UsageArea usageArea,
        @NotNull Finish finish,
        @NotNull BaseType baseType,
        BigDecimal coverageSqmPerLiter,
        BigDecimal dryingTimeHours,
        Boolean featured,
        Boolean active,
        @NotNull @Valid ProductTranslationsRequest translations,
        @Valid List<AttributeInput> attributes,
        @NotNull @Valid List<VariantInput> variants) {
}
