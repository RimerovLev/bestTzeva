package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.common.web.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Уникальные SKU вариантов (T-4.2.2). Генерирует по данным и подбирает свободный суффикс; гонку
 * ловит уникальный индекс product_variants.sku — вызывающий повторяет {@link #uniqueSku} при
 * конфликте вставки. Ручной SKU от админа: генерация пропускается, формат — мягко (предупреждение).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkuService {

    private static final int MAX_SUFFIX = 1000;

    private final JdbcTemplate jdbc;

    /** Свободный SKU: базовый или базовый-2, -3, … (проверка по БД). */
    public String uniqueSku(String brandCode, PaintType type, String colorCode, BigDecimal volume) {
        String base = SkuGenerator.generate(brandCode, type, colorCode, volume);
        if (!exists(base)) {
            return base;
        }
        for (int n = 2; n < MAX_SUFFIX; n++) {
            String candidate = base + "-" + n;
            if (!exists(candidate)) {
                return candidate;
            }
        }
        throw new ValidationException("catalog.sku.exhausted", base);
    }

    /** Ручной SKU: невалидный формат — предупреждение в лог, не ошибка (T-4.2.2). */
    public void validateManual(String sku) {
        if (!SkuGenerator.isValid(sku)) {
            log.warn("Manual SKU '{}' does not match recommended format BRAND-TYPE-COLOR-VOLUME", sku);
        }
    }

    private boolean exists(String sku) {
        Integer c = jdbc.queryForObject("select count(*) from product_variants where sku = ?", Integer.class, sku);
        return c != null && c > 0;
    }
}
