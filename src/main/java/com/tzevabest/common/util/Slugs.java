package com.tzevabest.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Генерация slug из произвольного текста (обычно английского названия). Правила едины для категорий,
 * товаров и портфолио: NFD-нормализация со снятием диакритики, нижний регистр, всё не [a-z0-9] → '-',
 * обрезка дефисов по краям. Коллизии разводятся числовым суффиксом.
 */
public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    /**
     * Уникальный slug: {@code base}, а при коллизии — {@code base-2}, {@code base-3}, …
     * Пустая база заменяется на {@code fallback}. {@code taken} — предикат «уже занят».
     */
    public static String unique(String base, String fallback, Predicate<String> taken) {
        String root = (base == null || base.isBlank()) ? fallback : base;
        String candidate = root;
        int suffix = 1;
        while (taken.test(candidate)) {
            suffix++;
            candidate = root + "-" + suffix;
        }
        return candidate;
    }
}
