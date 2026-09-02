package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.PaintType;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Генерация и валидация SKU варианта (T-4.2.2). Формат: {@code BRAND-TYPE-COLOR-VOLUME}, например
 * {@code TMB-INT-RAL9010-05L}. Чистая логика без Spring/БД (уникальность — в {@link SkuService}).
 */
public final class SkuGenerator {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9]{2,8}-[A-Z]{2,4}-[A-Z0-9]{1,16}-[0-9]{1,4}L$");

    private static final Map<PaintType, String> TYPE_CODES = Map.of(
            PaintType.INTERIOR, "INT", PaintType.EXTERIOR, "EXT", PaintType.WOOD, "WOD",
            PaintType.METAL, "MET", PaintType.PRIMER, "PRM", PaintType.ENAMEL, "ENL", PaintType.OTHER, "OTH");

    private SkuGenerator() {
    }

    /** Собирает SKU из данных варианта. */
    public static String generate(String brandCode, PaintType type, String colorCode, BigDecimal volumeLiters) {
        return String.join("-",
                clean(brandCode),
                TYPE_CODES.getOrDefault(type, "OTH"),
                clean(colorCode),
                volumeCode(volumeLiters));
    }

    /** Соответствует ли строка формату SKU. */
    public static boolean isValid(String sku) {
        return sku != null && FORMAT.matcher(sku).matches();
    }

    /**
     * Код объёма: литры без десятичной точки и хвостовых нулей, минимум 2 цифры, суффикс L.
     * 0.5 → 05L, 0.75 → 075L, 2.5 → 25L, 1 → 01L, 20 → 20L.
     */
    public static String volumeCode(BigDecimal volumeLiters) {
        // Литры без хвостовых нулей и десятичной точки; ведущий 0 (0.5, 0.75) значим — сохраняем.
        String digits = volumeLiters.stripTrailingZeros().toPlainString().replace(".", "");
        if (digits.length() < 2) {
            digits = "0" + digits; // 1 → 01
        }
        return digits + "L";
    }

    private static String clean(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
