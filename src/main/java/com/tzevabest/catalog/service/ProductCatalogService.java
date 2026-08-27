package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.catalog.dto.ProductCard;
import com.tzevabest.catalog.dto.VariantCard;
import com.tzevabest.common.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Публичное чтение каталога товаров (витрина): карточка товара с локализацией, денормализованными
 * ценой/наличием и активными вариантами. Без рейтинга/отзывов.
 */
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private static final String DEFAULT_LOCALE = "he";
    private static final List<String> LOCALES = List.of("he", "ru", "en");

    private final JdbcTemplate jdbc;

    /** Карточка активного товара. 404, если товар не найден или неактивен. */
    public ProductCard card(long id, String localeParam) {
        String locale = resolveLocale(localeParam);

        // Переводы: выбранная локаль и HE (подстановка).
        Map<String, String[]> tr = new HashMap<>();
        jdbc.query("select locale, name, short_description, description, usage_instructions "
                        + "from product_translations where product_id = ?",
                (RowCallbackHandler) rs -> tr.put(rs.getString("locale"), new String[]{
                        rs.getString("name"), rs.getString("short_description"),
                        rs.getString("description"), rs.getString("usage_instructions")}), id);
        String[] chosen = tr.getOrDefault(locale, tr.get(DEFAULT_LOCALE));

        List<VariantCard> variants = jdbc.query("""
                select v.id, v.sku, v.color_code, v.color_hex, v.volume_liters, v.price, v.old_price,
                       coalesce(a.available_qty, 0) as available
                from product_variants v
                left join v_variant_available a on a.variant_id = v.id
                where v.product_id = ? and v.is_active
                order by v.id
                """, (rs, n) -> {
                    int available = rs.getInt("available");
                    return new VariantCard(rs.getLong("id"), rs.getString("sku"), rs.getString("color_code"),
                            rs.getString("color_hex"), rs.getBigDecimal("volume_liters"), rs.getBigDecimal("price"),
                            rs.getBigDecimal("old_price"), available, available > 0);
                }, id);

        try {
            return jdbc.queryForObject("""
                    select sku, category_id, brand_id, paint_type, usage_area, finish, base_type,
                           coverage_sqm_per_liter, drying_time_hours, min_price, max_price, has_stock
                    from products where id = ? and is_active
                    """, (rs, n) -> new ProductCard(id, rs.getString("sku"), rs.getLong("category_id"),
                            rs.getLong("brand_id"), PaintType.valueOf(rs.getString("paint_type")),
                            UsageArea.valueOf(rs.getString("usage_area")), Finish.valueOf(rs.getString("finish")),
                            BaseType.valueOf(rs.getString("base_type")), rs.getBigDecimal("coverage_sqm_per_liter"),
                            rs.getBigDecimal("drying_time_hours"),
                            chosen != null ? chosen[0] : rs.getString("sku"),
                            chosen != null ? chosen[1] : null,
                            chosen != null ? chosen[2] : null,
                            chosen != null ? chosen[3] : null,
                            rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"), rs.getBoolean("has_stock"),
                            variants), id);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("catalog.product.not_found");
        }
    }

    private String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return DEFAULT_LOCALE;
    }
}
